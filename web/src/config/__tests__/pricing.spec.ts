import { describe, expect, it } from 'vitest'
import { pricingPlanFeatures } from '../pricing'

describe('pricingPlanFeatures', () => {
  it('only describes capabilities the current product can deliver', () => {
    expect(
      pricingPlanFeatures({
        code: 'GUARDIAN',
        photoLimit: 80,
        timelineLimit: 30,
        themeLimit: 3,
        hostedYears: 3,
        shortVideoLimit: 3,
      }),
    ).toEqual([
      '80 张照片、3 个短视频与 30 个时间线节点',
      '全部六套外观作品集，贯穿公开页、分享卡和纪念册',
      '多个时间胶囊与年度纪念册打印',
      '密码访问、分享卡片与完整导出',
      '3 年托管权益',
    ])
  })
})
