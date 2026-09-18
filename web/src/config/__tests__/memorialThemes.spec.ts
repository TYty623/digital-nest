import { describe, expect, it } from 'vitest'
import { hasThemeAccess, memorialThemeFor, memorialThemes } from '../memorialThemes'

describe('memorialThemes', () => {
  it('keeps six named art-direction contracts instead of six color aliases', () => {
    expect(memorialThemes).toHaveLength(6)
    expect(new Set(memorialThemes.map((theme) => theme.layout)).size).toBe(6)
    expect(memorialThemes.map((theme) => theme.name)).toEqual([
      '星夜来信', '窗边午后', '小小花园', '草地奔跑', '旧相册', '家里的灯',
    ])
  })

  it('keeps every sellable theme contract complete and uses server ownership', () => {
    for (const theme of memorialThemes) {
      expect(theme.art.photoFrame).not.toEqual('')
      expect(theme.art.timelineStyle).not.toEqual('')
      expect(theme.art.shareCard).not.toEqual('')
      expect(theme.art.yearbook).not.toEqual('')
      expect(theme.art.reducedMotion).not.toEqual('')
      expect(theme.typography.share).not.toEqual('')
    }
    expect(hasThemeAccess(memorialThemeFor('NIGHT'), { planCode: 'FREE', enabled: [] })).toBe(true)
    expect(hasThemeAccess(memorialThemeFor('SUNNY'), { planCode: 'FREE', enabled: [] })).toBe(false)
    expect(hasThemeAccess(memorialThemeFor('HOME'), { planCode: 'GUARDIAN', enabled: ['PREMIUM_APPEARANCE_PACK'], allThemeCollectionUnlocked: true })).toBe(true)
  })
})
