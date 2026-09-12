import { common } from '@kit.AbilityKit';
import { image } from '@kit.ImageKit';
import { font } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { Metrics, Palette } from '../common/Theme';
import { getSafeArea, SafeArea } from '../common/SafeArea';
import { PrintResult, PrinterConnection } from '../bluetooth/PrinterConnection';
import { AD_BANNER_MAX_LENGTH, renderAdBannerToPixelMap, THRESHOLD_TEXT } from '../bluetooth/RasterEncoder';
import { WIDTH_DOTS } from '../bluetooth/QringProtocol';
import { ConnState, getPrinterStatus, PrinterStatus } from '../model/PrinterStatus';
import { addHistory, HIST_TYPE_AD_BANNER } from '../model/HistoryStore';

const DOMAIN: number = 0x0001;
const TAG: string = 'AdBannerPrintPage';

/**
 * 「广告横带」打印页。
 *
 * 这个页面**不是**普通文字打印的衍生 —— 它是一个**锁定的固定模板模式**:
 *   - 字体:bold(系统无衬线,锁死,不可改)
 *   - 字号:系统根据「文字总长度 + 纸宽 + 横带最大长度」自动最大化,**用户不能调**
 *   - 对齐:整条文字作为一个整体渲染,**不能拆行、不能拆列、不能拆排**
 *   - 方向:整体顺时针旋转 90°
 *
 * 用户**唯一**能改的就是「内容」文本框;下面的字体/字号/加粗/对齐等控件
 * 全部以「锁定」状态显示出来,只是为了让用户理解当前是「固定模板」而不是
 * 普通文字模式,改动也不会真正影响渲染结果。
 *
 * 预览和打印走 renderAdBannerToPixelMap 同一份逻辑,所见即所打。
 */
@ComponentV2
export struct AdBannerPrintPage {
  /** 广告内容。允许换行 —— 内部会先压平再渲染 */
  @Local text: string = '';
  @Local printing: boolean = false;
  @Local resultMessage: string = '';
  @Local resultOk: boolean = false;

  // ── 预览 ──────────────────────────────────────────────
  @Local previewMap: image.PixelMap | undefined = undefined;
  @Local showPreview: boolean = false;
  /** 预览位图宽高(打印点),用来在白纸上画比例尺 */
  @Local previewDotsW: number = 0;
  @Local previewDotsH: number = 0;

  private status: PrinterStatus = getPrinterStatus();
  private safeArea: SafeArea = getSafeArea();
  private printer: PrinterConnection = PrinterConnection.getInstance();
  /**
   * 广告横带使用的字体族。**不允许用户在 UI 上切换**;此处取系统默认无衬线即可。
   * 之所以不暴露 Select 控件,是因为「广告横带」是一个锁定的固定模板。
   */
  private bannerFamily: string = 'sans-serif';

  aboutToAppear(): void {
    this.resolveFamily();
    this.restoreHistoryPayload();
  }

  /**
   * 解析一个尽量靠谱的字体族名。
   * 优先取系统字体列表里的第一个中文回退族;都拿不到时退回 sans-serif,
   * 与文字打印页的兜底策略保持一致。
   */
  private resolveFamily(): void {
    try {
      const installed: Array<string> = this.getUIContext().getFont().getSystemFontList();
      for (const name of installed) {
        if (name === 'sans-serif' || name === 'serif' || name === 'monospace') {
          continue;
        }
        this.bannerFamily = name;
        return;
      }
    } catch (err) {
      hilog.warn(DOMAIN, TAG, 'getSystemFontList unavailable: %{public}s', JSON.stringify(err));
    }
    try {
      const config: font.UIFontConfig = font.getUIFontConfig();
      for (const group of config.fallbackGroups) {
        for (const fallback of group.fallback) {
          if (fallback.family.length > 0) {
            this.bannerFamily = fallback.family;
            return;
          }
        }
      }
      for (const generic of config.generic) {
        if (generic.family.length > 0) {
          this.bannerFamily = generic.family;
          return;
        }
      }
    } catch (err) {
      hilog.warn(DOMAIN, TAG, 'getUIFontConfig unavailable: %{public}s', JSON.stringify(err));
    }
    this.bannerFamily = 'sans-serif';
  }

  /** 从历史记录恢复广告内容 */
  private restoreHistoryPayload(): void {
    const payload: string | undefined = AppStorage.get<string>('historyPayload');
    if (payload === undefined || payload.length === 0) {
      return;
    }
    AppStorage.setOrCreate('historyPayload', '');
    try {
      const p: Record<string, Object> = JSON.parse(payload) as Record<string, Object>;
      this.text = String(p['text'] ?? this.text);
    } catch (err) {
      // 解析失败则保持默认内容
    }
  }

  aboutToDisappear(): void {
    this.releasePreview();
  }

  /** PixelMap 是原生内存,换一张就得把上一张放掉 */
  private releasePreview(): void {
    if (this.previewMap !== undefined) {
      this.previewMap.release();
      this.previewMap = undefined;
    }
  }

  private connected(): boolean {
    return this.status.connState === ConnState.CONNECTED;
  }

  private toast(message: string): void {
    try {
      this.getUIContext().getPromptAction().showToast({ message: message, duration: 2000 });
    } catch (err) {
      // Previewer 下可能不可用
    }
  }

  /** 故障用弹窗而不是 Toast —— 这是拦停操作的硬条件,得让用户确认过再走 */
  private showFault(message: string): void {
    try {
      this.getUIContext().showAlertDialog({
        title: '无法打印',
        message: message,
        confirm: {
          value: '知道了',
          action: () => {},
        },
      });
    } catch (err) {
      this.toast(message);
    }
  }

  /**
   * 按当前内容生成一张横带位图作为预览。
   * 预览和打印共用 renderAdBannerToPixelMap,保证所见即所打。
   */
  private async openPreview(): Promise<void> {
    if (this.printing) {
      return;
    }
    if (this.flattenedLength() === 0) {
      this.toast('请输入广告内容');
      return;
    }
    try {
      this.releasePreview();
      const pm: image.PixelMap = renderAdBannerToPixelMap(this.text, this.bannerFamily);
      this.previewMap = pm;
      const info: image.ImageInfo = await pm.getImageInfo();
      this.previewDotsW = info.size.width;
      this.previewDotsH = info.size.height;
      this.showPreview = true;
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'preview render failed: %{public}s', JSON.stringify(err));
      this.toast('预览生成失败');
    }
  }

  /** 渲染前先压平(和 RasterEncoder.flattenAdBannerText 的规则一致)用于判空 */
  private flattenedLength(): number {
    if (this.text.length === 0) {
      return 0;
    }
    const replaced: string = this.text.replace(/[\r\n\t]+/g, ' ').replace(/ {2,}/g, ' ').trim();
    return replaced.length;
  }

  private async doPrint(): Promise<void> {
    if (this.printing) {
      return;
    }
    if (!this.connected()) {
      this.toast('请先在首页连接打印机');
      return;
    }
    if (this.flattenedLength() === 0) {
      this.toast('请输入广告内容');
      return;
    }

    this.printing = true;
    this.resultMessage = '';

    let pixelMap: image.PixelMap | null = null;
    try {
      // 打印前现查一次机器状态,缺纸/开盖/过热直接拦住
      const fault: string | null = await this.printer.preflightCheck();
      if (fault !== null) {
        this.showFault(fault);
        return;
      }

      pixelMap = renderAdBannerToPixelMap(this.text, this.bannerFamily);
      const result: PrintResult = await this.printer.printPixelMap(pixelMap, THRESHOLD_TEXT, 1);
      this.resultOk = result.ok;
      this.resultMessage = result.message;
      if (result.ok) {
        this.recordHistoryAsync();
      }
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'print failed: %{public}s', JSON.stringify(err));
      this.resultOk = false;
      this.resultMessage = '横带渲染失败';
    } finally {
      // PixelMap 是原生内存,不释放会累积泄漏
      if (pixelMap !== null) {
        pixelMap.release();
      }
      this.printing = false;
    }
  }

  /** 打印成功后记录历史(异步,不阻塞主线程) */
  private recordHistoryAsync(): void {
    const ctx: common.UIAbilityContext = this.getUIContext().getHostContext() as common.UIAbilityContext;
    const payload: string = JSON.stringify({
      text: this.text,
    });
    // 单独渲染一张给历史缩略图用 —— 不要复用 previewMap,
    // 避免在 addHistory 还没读完像素前被外层 release 把缩略图搞坏。
    let historyMap: image.PixelMap | null = null;
    try {
      historyMap = renderAdBannerToPixelMap(this.text, this.bannerFamily);
      addHistory(ctx, HIST_TYPE_AD_BANNER, historyMap, payload)
        .catch(() => {
          // 历史记录失败不影响打印结果
        })
        .finally(() => {
          // addHistory 内部 readPixelsSync 已经同步读走像素,这里释放不会影响结果
          if (historyMap !== null) {
            historyMap.release();
            historyMap = null;
          }
        });
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'record history failed: %{public}s', JSON.stringify(err));
      if (historyMap !== null) {
        historyMap.release();
      }
    }
  }

  // ── UI ────────────────────────────────────────────────

  @Builder
  connectionBanner() {
    Row() {
      Circle()
        .width(6)
        .height(6)
        .fill(this.connected() ? Palette.ONLINE : Palette.OFFLINE)
      Text(this.connected()
        ? `已连接 ${this.status.deviceName}`
        : '未连接打印机 —— 请回首页点状态卡选择设备')
        .fontSize(12)
        .fontColor(Palette.TEXT_SECONDARY)
        .margin({ left: 6 })
        .layoutWeight(1)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .padding({ top: 10, bottom: 10, left: 12, right: 12 })
    .backgroundColor(Palette.SURFACE)
    .borderRadius(10)
    .alignItems(VerticalAlign.Center)
  }

  /**
   * 锁定控件:展示「字体/字号/加粗/对齐」的固定值,明确告诉用户这些都是模板锁定的。
   * 灰底 + 不可点击 + 实际值不可改 —— 与普通文字打印页的同款控件形成视觉对比。
   */
  @Builder
  lockedField(label: string, value: string) {
    Row() {
      Text(label)
        .fontSize(13)
        .fontColor(Palette.TEXT_SECONDARY)
        .layoutWeight(1)
      Text(value)
        .fontSize(13)
        .fontColor(Palette.TEXT_SECONDARY)
        .opacity(0.7)
    }
    .width('100%')
    .height(40)
    .padding({ left: 12, right: 12 })
    .backgroundColor(Palette.SURFACE_SUNKEN)
    .borderRadius(8)
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  previewSheet() {
    Column() {
      Row() {
        Text(`纸宽 ${WIDTH_DOTS} 点 · 横带 ${this.previewDotsW} × ${this.previewDotsH} 点`)
          .fontSize(11)
          .fontColor(Palette.TEXT_SECONDARY)
          .layoutWeight(1)
        Text('已顺时针旋转 90°')
          .fontSize(11)
          .fontColor(Palette.BRAND)
      }
      .width('100%')
      .margin({ bottom: 10 })
      .alignItems(VerticalAlign.Center)

      Scroll() {
        if (this.previewMap !== undefined) {
          // 横带位图本身就是「纸宽方向 fontSize 宽、走纸方向 textWidth 长」,
          // 屏幕里按比例铺开即可。打印出来的纸也是同样的方向,所见即所打。
          Stack() {
            Image(this.previewMap)
              .width('100%')
              .height('100%')
              .interpolation(ImageInterpolation.None)
          }
          .width('100%')
          // aspectRatio 必须挂在 Stack 上,不能挂 Image:
          // 挂 Image 的话 Stack 自身没有确定高度,会被 Scroll 拉成整屏高。
          .aspectRatio(this.previewDotsH > 0 ? this.previewDotsW / this.previewDotsH : 1)
          .border({ width: 1, color: '#D8DCE3' })
          .backgroundColor('#FFFFFF')
        }
      }
      .layoutWeight(1)
      .align(Alignment.Top)
      .scrollBar(BarState.Auto)
      .backgroundColor(Palette.PAGE_BG)
      .padding(12)
    }
    .width('100%')
    .height('100%')
    .padding(Metrics.PAGE_PADDING)
    .alignItems(HorizontalAlign.Start)
    .backgroundColor(Palette.PAGE_BG)
  }

  build() {
    NavDestination() {
      Column() {
        this.connectionBanner()

        Scroll() {
          Column() {
            Text('广告内容')
              .fontSize(13)
              .fontWeight(FontWeight.Medium)
              .fontColor(Palette.TEXT_PRIMARY)
              .width('100%')
              .margin({ top: 16, bottom: 8 })

            TextArea({ placeholder: '输入广告内容,换行会被合并成同一行', text: $$this.text })
              .width('100%')
              .height(120)
              .fontSize(15)
              .backgroundColor(Palette.SURFACE)
              .borderRadius(12)
              .padding(12)
              .maxLength(500)
              .showCounter(true)
              .enabled(!this.printing)

            // 「固定模板」提示:把规则告诉用户,避免他们去找字体/字号控件
            Row() {
              Text('固定模板 · 排版参数已锁定')
                .fontSize(12)
                .fontColor(Palette.BRAND)
            }
            .width('100%')
            .margin({ top: 16, bottom: 8 })

            // 锁定的排版参数 —— 展示但不可改。
            // 用卡片框起来,跟普通文字打印页的「排版设置」区域在视觉上形成对照,
            // 用户一眼能看出「这里是另一个模式」。
            Column() {
              this.lockedField('字体', '系统默认(无衬线)')
              this.lockedField('字重', '粗体 Bold(强制)')
              this.lockedField('字号', '自动最大化')
              this.lockedField('排版', '单行 + 顺时针 90°')
              this.lockedField('对齐', '整条整体(不可拆分)')
            }
            .width('100%')
            .padding(8)
            .backgroundColor(Palette.SURFACE)
            .borderRadius(12)

            Text(`横带最大长度 ${AD_BANNER_MAX_LENGTH} 点 ≈ ${(AD_BANNER_MAX_LENGTH / 8).toFixed(0)}mm · 文字过长时字号会自动缩小以保证单行不溢出`)
              .fontSize(11)
              .fontColor(Palette.TEXT_SECONDARY)
              .width('100%')
              .margin({ top: 8, bottom: 8 })
          }
          .width('100%')
        }
        .layoutWeight(1)
        .align(Alignment.Top)
        .scrollBar(BarState.Off)
        .margin({ top: 12 })

        Row() {
          Button('打印预览')
            .layoutWeight(1)
            .height(48)
            .fontSize(15)
            .fontColor(Palette.BRAND)
            .backgroundColor(Palette.SURFACE)
            .opacity(this.printing || this.flattenedLength() === 0 ? 0.4 : 1)
            .enabled(!this.printing && this.flattenedLength() > 0)
            .onClick(() => {
              this.openPreview();
            })

          Button(this.printing ? '打印中…' : '打印')
            .layoutWeight(1)
            .height(48)
            .fontSize(16)
            .fontColor(Palette.TEXT_ON_BRAND)
            .backgroundColor(this.printing || !this.connected() ? Palette.OFFLINE : Palette.BRAND)
            .enabled(!this.printing)
            .margin({ left: 12 })
            .onClick(() => {
              this.doPrint();
            })
        }
        .width('100%')
        .margin({ top: 12 })
        .bindSheet($$this.showPreview, this.previewSheet(), {
          height: SheetSize.LARGE,
          preferType: SheetType.BOTTOM,
          title: { title: '广告横带预览' },
          backgroundColor: Palette.PAGE_BG,
          onDisappear: () => {
            this.showPreview = false;
          },
        })

        if (this.resultMessage.length > 0) {
          Text(this.resultMessage)
            .fontSize(13)
            .fontColor(this.resultOk ? Palette.ONLINE : Palette.DANGER)
            .width('100%')
            .margin({ top: 10 })
        }
      }
      .width('100%')
      .height('100%')
      .padding(Metrics.PAGE_PADDING)
      .alignItems(HorizontalAlign.Start)
      .backgroundColor(Palette.PAGE_BG)
    }
    .title('广告横带')
    .padding({ top: this.safeArea.top })
    .backgroundColor(Palette.PAGE_BG)
  }
}
