/**
 * 海报绘制共用工具。
 * 打卡、周小结、运动轨迹三张海报都是「整张底图 + 底部信息条」的结构，
 * 这里收敛底图加载、文字测量、信息条底色、数据列排布这些重复逻辑。
 */

export const POSTER_FONT = "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif"

/** 信息条底色与顶部渐变色条，三张海报保持同一视觉语言 */
export const BAND_BG = '#17251b'
const ACCENT_FROM = '#a8dc5c'
const ACCENT_TO = '#4a7d24'

export function loadPosterImage(src) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('poster image failed to load'))
    image.src = src
  })
}

export function canvasToBlob(canvas, type = 'image/png') {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('poster encode failed'))
    }, type)
  })
}

/** 单行截断，超出宽度时补省略号 */
export function clipText(ctx, text, maxWidth) {
  let output = String(text ?? '')
  while (output.length > 1 && ctx.measureText(output).width > maxWidth) {
    output = output.slice(0, -1)
  }
  return output.length < String(text ?? '').length ? `${output}…` : output
}

/** 按字符宽度折行，中文逐字判断即可 */
export function wrapLines(ctx, text, maxWidth) {
  const lines = []
  let line = ''
  for (const char of String(text ?? '')) {
    if (char === '\n') {
      lines.push(line)
      line = ''
      continue
    }
    if (ctx.measureText(line + char).width > maxWidth) {
      lines.push(line)
      line = char
    } else {
      line += char
    }
  }
  if (line) lines.push(line)
  return lines
}

export function roundRectPath(ctx, x, y, width, height, radius) {
  if (typeof ctx.roundRect === 'function') {
    ctx.beginPath()
    ctx.roundRect(x, y, width, height, radius)
    return
  }
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + width, y, x + width, y + height, radius)
  ctx.arcTo(x + width, y + height, x, y + height, radius)
  ctx.arcTo(x, y + height, x, y, radius)
  ctx.arcTo(x, y, x + width, y, radius)
  ctx.closePath()
}

/** 画信息条底色 + 顶部渐变高光 */
export function drawBandChrome(ctx, width, bandTop, bandHeight) {
  ctx.fillStyle = BAND_BG
  ctx.fillRect(0, bandTop, width, bandHeight)

  const accent = ctx.createLinearGradient(0, 0, width, 0)
  accent.addColorStop(0, ACCENT_FROM)
  accent.addColorStop(1, ACCENT_TO)
  ctx.fillStyle = accent
  ctx.fillRect(0, bandTop, width, 6)
}

/**
 * 排布若干组「标签 + 数值 + 单位」。
 * 默认从 right 往左右对齐（适合左侧已有标题的信息条）；
 * 传 spread 则从 left 到 right 均分铺满（适合独占一行的数据栏）。
 */
export function drawStatColumns(
  ctx,
  stats,
  { left = 64, right, labelY, valueY, gap = 52, valueSize = 44, spread = false }
) {
  const columns = stats.map((stat) => {
    const text = String(stat.value ?? 0)
    ctx.font = `bold ${valueSize}px ${POSTER_FONT}`
    const valueWidth = ctx.measureText(text).width
    ctx.font = `22px ${POSTER_FONT}`
    const unitWidth = stat.unit ? ctx.measureText(stat.unit).width : 0
    const labelWidth = ctx.measureText(stat.label).width
    return {
      text,
      valueWidth,
      width: Math.max(labelWidth, valueWidth + (stat.unit ? 6 + unitWidth : 0))
    }
  })

  const columnsWidth = columns.reduce((sum, column) => sum + column.width, 0)
  const gapCount = Math.max(0, columns.length - 1)
  const gapValue = spread && gapCount > 0
    ? Math.max(24, (right - left - columnsWidth) / gapCount)
    : gap
  const total = columnsWidth + gapValue * gapCount
  let cursorX = spread ? left : right - total

  stats.forEach((stat, index) => {
    const column = columns[index]
    ctx.fillStyle = 'rgba(255,255,255,0.5)'
    ctx.font = `22px ${POSTER_FONT}`
    ctx.fillText(stat.label, cursorX, labelY)

    ctx.fillStyle = '#ffffff'
    ctx.font = `bold ${valueSize}px ${POSTER_FONT}`
    ctx.fillText(column.text, cursorX, valueY)

    if (stat.unit) {
      ctx.fillStyle = 'rgba(255,255,255,0.58)'
      ctx.font = `22px ${POSTER_FONT}`
      ctx.fillText(stat.unit, cursorX + column.valueWidth + 6, valueY)
    }

    cursorX += column.width + gapValue
  })

  return total
}
