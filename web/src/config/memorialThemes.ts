import type { CSSProperties } from 'vue'
import type { AccountCapabilities, Memorial } from '@/api/memorials'

export type MemorialThemeCode = Memorial['theme']
export type ThemeLayout = 'letter' | 'window' | 'garden' | 'meadow' | 'album' | 'home'

type ThemePalette = { canvas: string; surface: string; surfaceSoft: string; ink: string; muted: string; accent: string; accentText: string; border: string }
type ThemeTypography = { display: string; body: string; share: string }
type ThemeArtDirection = {
  publicLayout: ThemeLayout
  roomMaterial: string
  photoFrame: string
  photoRatio: string
  timelineStyle: string
  letterStyle: string
  backgroundTexture: string
  shareCard: string
  yearbook: string
  emptyState: string
  motion: string
  reducedMotion: string
}

export type MemorialTheme = {
  code: MemorialThemeCode
  name: string
  shortName: string
  description: string
  layout: ThemeLayout
  palette: ThemePalette
  typography: ThemeTypography
  art: ThemeArtDirection
  capability: 'FREE' | 'PREMIUM_APPEARANCE_PACK'
}

/** The single theme registry for the public page, room, sharing and yearbook. */
export const memorialThemes: readonly MemorialTheme[] = [
  {
    code: 'NIGHT', name: '星夜来信', shortName: '星夜', layout: 'letter', description: '深海、旧金与书信留白，让一段具体的生活细节先出现。',
    palette: { canvas: '#141f2c', surface: '#1c2a38', surfaceSoft: '#223240', ink: '#efe9dc', muted: '#acb6be', accent: '#d8be88', accentText: '#172432', border: '#3d4b59' },
    typography: { display: '"Noto Serif SC", "Songti SC", serif', body: '"PingFang SC", "Microsoft YaHei", sans-serif', share: '"Noto Serif SC", Georgia, serif' },
    art: { publicLayout: 'letter', roomMaterial: '深色木与黄铜', photoFrame: '月光窄框', photoRatio: '4:5', timelineStyle: '星间信笺', letterStyle: '窄栏书信', backgroundTexture: '低对比夜空与远距离光点', shareCard: '左侧金线书信卡', yearbook: '深夜信封式扉页', emptyState: '留一封还未写完的信纸', motion: '灯光缓慢呼吸，内容静静显现', reducedMotion: '关闭灯光呼吸与淡入，保留静态光源' }, capability: 'FREE',
  },
  {
    code: 'SUNNY', name: '窗边午后', shortName: '午后', layout: 'window', description: '亚麻、窗光与暖白相纸，把日常并排放在一张桌面上。',
    palette: { canvas: '#f7f2ea', surface: '#fffdf9', surfaceSoft: '#f2e8db', ink: '#2b2926', muted: '#665f58', accent: '#8a4f3d', accentText: '#fffdf9', border: '#ded6cb' },
    typography: { display: '"Noto Serif SC", "Songti SC", serif', body: '"PingFang SC", "Microsoft YaHei", sans-serif', share: '"Noto Serif SC", Georgia, serif' },
    art: { publicLayout: 'window', roomMaterial: '木、布与玻璃', photoFrame: '暖白相纸', photoRatio: '3:2', timelineStyle: '窗边光影', letterStyle: '桌面便笺', backgroundTexture: '亚麻纹理与斜向窗光', shareCard: '窗影留白卡', yearbook: '居家桌面式跨页', emptyState: '窗边留一把等待放入照片的椅子', motion: '窗光极慢移动', reducedMotion: '固定窗光位置，不播放光线移动' }, capability: 'PREMIUM_APPEARANCE_PACK',
  },
  {
    code: 'GARDEN', name: '小小花园', shortName: '花园', layout: 'garden', description: '压花、植物标本与季节留白，像把记忆收进一本册子。',
    palette: { canvas: '#edf3e8', surface: '#f9fbf6', surfaceSoft: '#e4eddd', ink: '#334333', muted: '#52624b', accent: '#466239', accentText: '#f9fbf6', border: '#bccab1' },
    typography: { display: '"Noto Serif SC", "Songti SC", serif', body: '"PingFang SC", "Microsoft YaHei", sans-serif', share: '"Noto Serif SC", Georgia, serif' },
    art: { publicLayout: 'garden', roomMaterial: '陶盆、压花与纸', photoFrame: '植物标本夹', photoRatio: '4:5', timelineStyle: '植物采集册', letterStyle: '压花卡片', backgroundTexture: '植物标本与水彩叶影', shareCard: '标本标签式卡片', yearbook: '四季植物册', emptyState: '留一页等待被夹入的叶片', motion: '季节色轻微过渡', reducedMotion: '使用固定季节色，不做过渡' }, capability: 'PREMIUM_APPEARANCE_PACK',
  },
  {
    code: 'MEADOW', name: '草地奔跑', shortName: '草地', layout: 'meadow', description: '开阔横幅照片、草纹与有风感的阅读路径。',
    palette: { canvas: '#e8f1e6', surface: '#fbfcf7', surfaceSoft: '#dcefe9', ink: '#29423b', muted: '#5f766c', accent: '#427c64', accentText: '#fbfcf7', border: '#b9d1c5' },
    typography: { display: '"Noto Sans SC", "PingFang SC", sans-serif', body: '"PingFang SC", "Microsoft YaHei", sans-serif', share: '"Noto Sans SC", Arial, sans-serif' },
    art: { publicLayout: 'meadow', roomMaterial: '草地、天空与脚印', photoFrame: '宽幅草边框', photoRatio: '16:9', timelineStyle: '有风的路径', letterStyle: '野餐纸条', backgroundTexture: '低对比草纹与天空留白', shareCard: '宽幅旅行卡', yearbook: '横向散步地图', emptyState: '留一条还未走完的草地小径', motion: '草纹极慢平移', reducedMotion: '草纹保持静止' }, capability: 'PREMIUM_APPEARANCE_PACK',
  },
  {
    code: 'ALBUM', name: '旧相册', shortName: '相册', layout: 'album', description: '纸张纤维、相片角贴与手写日期，让照片成为叙事的第一句。',
    palette: { canvas: '#eee7db', surface: '#fbf8f0', surfaceSoft: '#eee2cc', ink: '#3d352c', muted: '#76685b', accent: '#8b6045', accentText: '#fbf8f0', border: '#d8c9b5' },
    typography: { display: '"Noto Serif SC", "STKaiti", serif', body: '"PingFang SC", "Microsoft YaHei", sans-serif', share: '"Noto Serif SC", Georgia, serif' },
    art: { publicLayout: 'album', roomMaterial: '纸张、胶角与木盒', photoFrame: '白边相纸与角贴', photoRatio: '4:5', timelineStyle: '翻阅索引', letterStyle: '日期印章', backgroundTexture: '纸张纤维与轻微颗粒', shareCard: '拍立得日期卡', yearbook: '可翻阅旧相册', emptyState: '留一个等待贴上第一张照片的角贴', motion: '翻页淡入', reducedMotion: '取消旋转与翻页，只保留静态排版' }, capability: 'PREMIUM_APPEARANCE_PACK',
  },
  {
    code: 'HOME', name: '家里的灯', shortName: '家灯', layout: 'home', description: '深木、暖灯与布料，围绕床、碗、玩具和门口的生活痕迹。',
    palette: { canvas: '#2a2521', surface: '#393028', surfaceSoft: '#4a3d32', ink: '#f4eadc', muted: '#c9bbaa', accent: '#d9ae71', accentText: '#2a2521', border: '#695749' },
    typography: { display: '"Noto Serif SC", "Songti SC", serif', body: '"PingFang SC", "Microsoft YaHei", sans-serif', share: '"Noto Serif SC", Georgia, serif' },
    art: { publicLayout: 'home', roomMaterial: '布料、木纹与暖灯', photoFrame: '家庭照片墙', photoRatio: '1:1', timelineStyle: '家中走廊', letterStyle: '灯下纸页', backgroundTexture: '深木纹与柔和灯晕', shareCard: '灯下相框卡', yearbook: '家中照片墙式版面', emptyState: '让一盏灯先照着还没说完的日常', motion: '操作后柔和收束', reducedMotion: '灯晕固定，不使用收束动画' }, capability: 'PREMIUM_APPEARANCE_PACK',
  },
] as const

export function memorialThemeFor(code: MemorialThemeCode | string | null | undefined): MemorialTheme {
  return memorialThemes.find((theme) => theme.code === code) ?? memorialThemes[0]!
}

export function themeStyleVars(code: MemorialThemeCode | string | null | undefined): CSSProperties {
  const theme = memorialThemeFor(code)
  return {
    '--canvas': theme.palette.canvas, '--surface': theme.palette.surface, '--surface-soft': theme.palette.surfaceSoft,
    '--text': theme.palette.ink, '--muted': theme.palette.muted, '--primary': theme.palette.accent,
    '--button-text': theme.palette.accentText, '--border': theme.palette.border,
    '--font-serif': theme.typography.display, '--font-body': theme.typography.body, '--font-share': theme.typography.share,
  } as CSSProperties
}

export function hasThemeAccess(theme: MemorialTheme, capabilities: AccountCapabilities | null): boolean {
  return theme.capability === 'FREE' || Boolean(capabilities?.allThemeCollectionUnlocked) || Boolean(capabilities?.enabled.includes(theme.capability))
}

export function themeClass(code: MemorialThemeCode | string | null | undefined) {
  return `theme-${memorialThemeFor(code).code.toLowerCase()}`
}
