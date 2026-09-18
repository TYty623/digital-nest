export const pricingPageContent = {
  eyebrow: '套餐与托管',
  titleFirstLine: '让价格说清楚，',
  titleSecondLine: '让回忆不用猜。',
  description:
    '查看、留言、基础仪式、资料导出和删除权都不收费。会员购买的是更多储存空间、时间胶囊、年度纪念册与分阶段开放的数字记忆能力。当前公测开放免费体验；真实微信支付会在商户审核完成后开放。',
  customService: {
    eyebrow: '没有精力慢慢整理也没关系',
    title: '专属定制 ¥699 起',
    description:
      '由我们协助整理材料、润色故事、搭建页面。固定交付范围，包含两轮修改与 7 个工作日内交付。现在可先提交服务申请；不会在站内产生扣款。',
    policyLabel: '查看支付开放与退款说明 →',
    actionLabel: '提交定制申请',
  },
} as const

export interface PricingPlanContent {
  readonly code: string
  readonly name: string
  readonly note: string
  readonly featured?: boolean
}

export const pricingPlans: readonly PricingPlanContent[] = [
  {
    code: 'FREE',
    name: '免费体验',
    note: '先把第一份记忆安放好',
  },
  {
    code: 'GUARDIAN',
    name: '温暖守护',
    note: '适合大多数完整纪念页',
    featured: true,
  },
  {
    code: 'TREASURE',
    name: '时光珍藏',
    note: '为全家保存更多故事',
  },
] as const

export type PlanCapabilities = {
  code: string
  photoLimit: number
  shortVideoLimit: number
  timelineLimit: number
  themeLimit: number
  hostedYears: number
}

export function pricingPlanFeatures(plan: PlanCapabilities) {
  const themeDescription =
    plan.code === 'FREE'
      ? '星夜来信一套完整基础主题'
      : '全部六套外观作品集，贯穿公开页、分享卡和纪念册'
  const mediaDescription =
    plan.shortVideoLimit > 0
      ? `${plan.photoLimit} 张照片、${plan.shortVideoLimit} 个短视频与 ${plan.timelineLimit} 个时间线节点`
      : `${plan.photoLimit} 张照片与 ${plan.timelineLimit} 个时间线节点`
  const sharedFeatures = [mediaDescription, themeDescription]

  if (plan.code === 'FREE') return ['1 个纪念页', ...sharedFeatures, '基础仪式、1 个时间胶囊与完整导出']
  if (plan.code === 'GUARDIAN') return [...sharedFeatures, '多个时间胶囊与年度纪念册打印', '密码访问、分享卡片与完整导出', `${plan.hostedYears} 年托管权益`]
  if (plan.code === 'TREASURE') return [...sharedFeatures, '数字记忆档案与可追溯记忆问答', '多个时间胶囊与年度纪念册打印', '声音与数字形象仅预留接口，尚未开放', `${plan.hostedYears} 年托管权益`]
  return [...sharedFeatures, '人工整理与专属服务', `${plan.hostedYears} 年托管权益`]
}
