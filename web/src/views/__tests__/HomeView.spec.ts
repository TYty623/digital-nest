import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import HomeView from '../HomeView.vue'

describe('HomeView', () => {
  it('explains the product and points people to the creation flow', () => {
    const wrapper = mount(HomeView, {
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>',
          },
        },
      },
    })

    expect(wrapper.text()).toContain('记住 TA')
    expect(wrapper.text()).toContain('不只是怎样离开。')
    expect(wrapper.get('a[href="/create"]').text()).toContain('开始记录 TA 的一天')
    expect(wrapper.get('a[href="/community"]').text()).toContain('看看今天被想起的 TA')
  })
})
