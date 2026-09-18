import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ExamplesView from '../ExamplesView.vue'

describe('ExamplesView', () => {
  it('links each theme card to a complete demo memorial', () => {
    const wrapper = mount(ExamplesView, {
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>',
          },
        },
      },
    })

    expect(wrapper.get('a[href="/examples/doubao"]').text()).toContain('查看完整演示')
    expect(wrapper.get('a[href="/examples/tuantuan"]').text()).toContain('查看完整演示')
    expect(wrapper.get('a[href="/examples/xiaomai"]').text()).toContain('查看完整演示')
  })
})
