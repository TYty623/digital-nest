<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import heroMemoryCorner from '@/assets/hero-memory-corner-v1.webp'
import xiaomaiWindow from '@/assets/xiaomai-window-v1.webp'
import { useRevealMotion } from '@/composables/useRevealMotion'

const activeEcho = ref(0)
const echoPrompts = [
  { label: '一个动作', prompt: 'TA 等你回家时，身体会先做什么？', example: '先竖起耳朵，再小跑到门口，尾巴撞得鞋柜轻轻响。' },
  { label: '一种声音', prompt: '闭上眼睛，你最先想起什么声音？', example: '喝水时项圈碰到碗边，那两声很轻的叮当。' },
  { label: '一个位置', prompt: '家里哪一小块地方，永远像是 TA 的？', example: '下午两点，客厅最靠左的那块阳光。' },
  { label: '一件旧物', prompt: '哪件东西一拿起来，就会立刻想到 TA？', example: '那只已经咬掉一只耳朵的蓝色小熊。' },
]

useRevealMotion()
const steps = [
  { note: '先从时间开始', title: '还原 TA 的一天', body: '几点醒来、在哪里晒太阳、什么时候等你回家，把普通的一天慢慢拼回来。' },
  { note: '只写家人才懂的', title: '留下生命指纹', body: '昵称、习惯、声音、花纹和最爱的角落，记住只有家人才知道的 TA。' },
  { note: '收好生活的痕迹', title: '安放照片与旧物', body: '把相册、声音、项圈和未说完的话，收进真正属于 TA 的记忆陈列馆。' },
]
</script>

<template>
  <section class="section night-home-hero" data-reveal>
    <div>
      <p class="eyebrow">FOR THE ONES WHO LIT UP OUR DAYS</p>
      <h1>记住 TA 怎样生活过，<br /><em>不只是怎样离开。</em></h1>
      <p class="night-intro">从一个平常日子开始，留下习惯、声音和最爱的角落。</p>
      <div class="hero-actions">
        <RouterLink class="button" to="/create">开始记录 TA 的一天 ↗</RouterLink
        ><RouterLink class="button button-quiet" to="/community">看看今天被想起的 TA</RouterLink>
      </div>
    </div>
    <figure class="night-hero-visual">
      <img :src="heroMemoryCorner" width="800" height="1000" fetchpriority="high" decoding="async" alt="蓝色暮光下，窗边放着有使用痕迹的宠物小窝、玩具和水碗" />
      <figcaption><span>一个平常的角落</span><strong>也记得 TA 怎样生活过。</strong></figcaption>
    </figure>
  </section>
  <div class="section night-ribbon" data-reveal>
    <span>免费开始</span><span>默认仅链接可见</span><span>照片与文字可导出</span><span>亲友查看不收费</span>
  </div>
  <section id="letter" class="section night-letter" data-reveal>
    <aside class="night-letter-meta">
      <span>演示纪念故事</span><strong>小麦</strong>2018 - 2025
      <figure class="night-letter-portrait">
        <img :src="xiaomaiWindow" width="640" height="853" loading="lazy" decoding="async" alt="演示故事中的小麦在窗边晒太阳" />
        <figcaption>小麦最喜欢的窗边</figcaption>
      </figure>
    </aside>
    <div class="night-letter-copy">
      <p class="letter-salutation">DEAR MY LITTLE FRIEND,</p>
      <h2>我还是会在开门前，<br />想起你跑过来的声音。</h2>
      <p>
        以前总觉得，陪你散步只是一天里很普通的一件事。现在才发现，那些有风的傍晚，我们其实拥有过很多很多。
      </p>
      <p>你的旧玩具还在窗边。阳光来的时候，就像你又在那里睡了一个很长的午觉。</p>
      <p>谢谢你，把那么普通的日子，变成了我最舍不得的记忆。</p>
      <RouterLink class="text-link" to="/examples/xiaomai">走进小麦的小窝 ↗</RouterLink>
    </div>
  </section>
  <section class="section memory-sampler" data-reveal>
    <div class="memory-sampler-heading">
      <p class="eyebrow">MEMORY SAMPLER / 试着想起一个细节</p>
      <h2>不是要写完整的一生。<br />先接住一个突然想起的瞬间。</h2>
      <p>点开一个问题，看看真正属于 TA 的故事可以从哪里开始。</p>
    </div>
    <div class="memory-sampler-stage">
      <div class="memory-prompt-list" role="tablist" aria-label="记忆提示">
        <button
          v-for="(item, index) in echoPrompts"
          :key="item.label"
          type="button"
          role="tab"
          :aria-selected="activeEcho === index"
          :class="{ active: activeEcho === index }"
          @click="activeEcho = index"
        >
          <strong>{{ item.label }}</strong>
        </button>
      </div>
      <article class="memory-echo" aria-live="polite">
        <span class="memory-echo-mark" aria-hidden="true">✦</span>
        <p>{{ echoPrompts[activeEcho]?.prompt }}</p>
        <blockquote>“{{ echoPrompts[activeEcho]?.example }}”</blockquote>
        <RouterLink class="text-link" to="/create">从这个细节开始记录 ↗</RouterLink>
      </article>
    </div>
  </section>
  <section class="section night-community-callout" data-reveal>
    <div><p class="section-kicker">纪念星河</p><h2>看见别人的一小段记得，<br />也知道自己的想念并不孤单。</h2><p>这里只展示主人主动公开的照片、昵称和一条生活细节。没有排名、关注与私信。</p></div>
    <RouterLink class="button button-quiet" to="/community">走进纪念星河 ↗</RouterLink>
  </section>
  <section class="section night-how" data-reveal>
    <div class="section-heading with-action">
      <h2>每一颗微光，<br />都是生活过的痕迹。</h2>
      <p class="night-intro">不必把想念说得完整。<br />从一个片段开始，慢慢拼回 TA 的样子。</p>
    </div>
    <ol class="night-steps">
      <li v-for="step in steps" :key="step.title">
        <span>{{ step.note }}</span>
        <h3>{{ step.title }}</h3>
        <p>{{ step.body }}</p>
      </li>
    </ol>
  </section>
  <section class="section night-promise" data-reveal>
    <h2>让记忆，安稳地留下。</h2>
    <div class="night-steps">
      <div>
        <h3>谁能看，由你决定</h3>
        <p>仅自己、持链接、口令访问或公开。你可以随时调整，也可以暂时下线。</p>
      </div>
      <div>
        <h3>保存，也能带走</h3>
        <p>照片、文字和时间线支持打包导出。留一份在这里，也留一份在自己手里。</p>
      </div>
      <div>
        <h3>想念没有付费门槛</h3>
        <p>亲友查看和留言不收费。需要更多容量时，再选择适合的托管计划。</p>
      </div>
    </div>
    <RouterLink class="text-link" to="/pricing">查看容量与托管计划 ↗</RouterLink>
  </section>
  <section class="section night-questions" data-reveal>
    <h2>你可能想知道</h2>
    <details>
      <summary>还没准备好写故事，可以先存照片吗？</summary>
      <p>当然。先选一张照片、写下昵称，保存成只有自己能看的草稿，之后慢慢整理。</p>
    </details>
    <details>
      <summary>照片和文字会被公开吗？</summary>
      <p>
        草稿不会公开。发布时可选择持链接访问、口令访问或公开；仅自己可见的内容不能分享。持链接访问不等于加密保密，请谨慎转发。
      </p>
    </details>
    <details>
      <summary>可以修改已经发布的内容吗？</summary>
      <p>可以。整理台保存后，更新会同步到已发布页面。如果想先安静整理，可暂时下线，再恢复分享。</p>
    </details>
    <RouterLink class="text-link" to="/help">更多使用帮助 →</RouterLink>
  </section>
  <section class="section night-final" data-reveal>
    <h2>今晚，想对 TA 说些什么？</h2>
    <RouterLink class="button" to="/create">写下第一句话 ↗</RouterLink>
  </section>
</template>
