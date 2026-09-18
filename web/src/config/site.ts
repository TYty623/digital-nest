import type { CustomServiceStatus } from '@/api/memorials'

/** Frequently edited site copy and navigation. Keep page components focused on interaction. */
export const siteBrand = {
  name: '数字小窝',
  descriptor: '宠物数字纪念',
  homeAriaLabel: '数字小窝首页',
  footerDescription: '把关于 TA 的照片和故事，安放在一处随时可以回来看看的地方。',
} as const

export const headerNavigation = [
  { to: '/account', label: '我的纪念' },
  { to: '/habitat', label: '记忆角落' },
  { to: '/community', label: '纪念星河' },
  { to: '/pricing', label: '会员权益' },
  { to: '/help', label: '帮助' },
] as const

export const footerNavigation = [
  { to: '/community', label: '纪念星河' },
  { to: '/help', label: '帮助中心' },
  { to: '/pricing', label: '套餐说明' },
  { to: '/privacy', label: '隐私政策' },
  { to: '/terms', label: '用户协议' },
  { to: '/content', label: '内容规范' },
  { to: '/refund', label: '支付与退款说明' },
] as const

export const customServiceStatusLabels: Record<CustomServiceStatus, string> = {
  SUBMITTED: '已提交，待确认范围',
  MATERIALS_PENDING: '等待补充材料',
  IN_PROGRESS: '正在整理制作',
  REVISION: '等待或处理修改',
  OUT_OF_SCOPE: '超出当前约定范围',
  DELIVERED: '已交付，等待你确认',
  COMPLETED: '已完成',
  CANCELED: '已取消',
}
