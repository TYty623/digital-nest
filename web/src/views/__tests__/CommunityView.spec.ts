import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import CommunityView from '../CommunityView.vue'
import { discoverMemorials, lightMemorial } from '@/api/memorials'

vi.mock('@/api/memorials', () => ({
  discoverMemorials: vi.fn<typeof discoverMemorials>(),
  lightMemorial: vi.fn<typeof lightMemorial>(),
}))

describe('CommunityView', () => {
  beforeEach(() => {
    vi.mocked(discoverMemorials).mockResolvedValue([
      {
        id: '1', slug: 'xingxing', petName: '星星', species: '小狗', coverImageUrl: null,
        companionStartedOn: '2013-03-01', companionEndedOn: '2026-08-01',
        signature: '每天傍晚都在门口等我。', lightCount: 2, publishedAt: '2026-09-08T00:00:00Z',
      },
      {
        id: '2', slug: 'mimi', petName: '咪咪', species: '小猫', coverImageUrl: null,
        companionStartedOn: null, companionEndedOn: null,
        signature: '最喜欢在阳台晒太阳。', lightCount: 0, publishedAt: '2026-09-07T00:00:00Z',
      },
    ])
  })

  it('shows only the public summary returned by the server and supports gentle filters', async () => {
    const wrapper = mount(CommunityView, {
      global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('星星')
    expect(wrapper.text()).toContain('每天傍晚都在门口等我。')
    await wrapper.get('.community-toolbar button:nth-child(3)').trigger('click')
    expect(wrapper.text()).not.toContain('星星')
    expect(wrapper.text()).toContain('咪咪')
  })
})
