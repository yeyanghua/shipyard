/**
 * PagePlaceholder 组件测试.
 *
 * <p>验证 props 渲染: title / milestone / description 三个 prop 都正确显示.
 */
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PagePlaceholder from '../PagePlaceholder.vue';

describe('PagePlaceholder', () => {
  it('renders title as h1', () => {
    const wrapper = mount(PagePlaceholder, {
      props: { title: '总览', milestone: 'M3 stub' },
    });
    expect(wrapper.find('h1').text()).toBe('总览');
  });

  it('renders milestone in badge', () => {
    const wrapper = mount(PagePlaceholder, {
      props: { title: 'X', milestone: 'M4 + M13' },
    });
    expect(wrapper.find('.badge').text()).toContain('M4 + M13');
  });

  it('renders description when provided', () => {
    const wrapper = mount(PagePlaceholder, {
      props: { title: 'X', milestone: 'M4', description: '测试描述' },
    });
    expect(wrapper.text()).toContain('测试描述');
  });

  it('omits description paragraph when not provided', () => {
    const wrapper = mount(PagePlaceholder, {
      props: { title: 'X', milestone: 'M4' },
    });
    // 只有 hint 段落,没 description
    const paragraphs = wrapper.findAll('p');
    expect(paragraphs.length).toBe(1);
    expect(paragraphs[0].classes()).toContain('hint');
  });
});
