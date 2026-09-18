export type MemorialExample = {
  slug: string
  name: string
  species: string
  kind: string
  years: string
  theme: 'sun' | 'night' | 'garden'
  coverSymbol: string
  quote: string
  timeline: Array<{ date: string; title: string; body: string }>
  gallery: Array<{ symbol: string; caption: string }>
  letter: { subject: string; body: string }
  tributes: Array<{ author: string; message: string }>
  lightCount: number
}

export const memorialExamples: MemorialExample[] = [
  {
    slug: 'doubao', name: '豆包', species: '小狗', kind: '暖阳相册', years: '2012 - 2025', theme: 'sun', coverSymbol: '☀', quote: '把每一个蹭过来的早晨，都留在这里。', lightCount: 128,
    timeline: [
      { date: '2012 年初夏', title: '第一次回家', body: '纸箱里探出一个小脑袋，从此门口多了一双等人的眼睛。' },
      { date: '2017 年秋天', title: '学会一起散步', body: '他总会走在半步之前，又在拐角处回头确认我们都跟上了。' },
      { date: '2025 年春天', title: '把阳光留给我们', body: '最后一个午后，他还像从前一样，在窗边晒着太阳。' },
    ],
    gallery: [{ symbol: '⌂', caption: '刚到家时，耳朵还没有立起来。' }, { symbol: '♧', caption: '最喜欢去楼下闻一闻风。' }, { symbol: '☕', caption: '每个周末都要陪我们赖床。' }],
    letter: { subject: '谢谢你，把每一天都变得热闹。', body: '豆包，谢谢你一直等我回家。现在家里安静了一点，但我们还是会在每一个有阳光的早晨想起你。' },
    tributes: [{ author: '阿姨', message: '记得他每次见面都会摇着尾巴跑过来。' }, { author: '小林', message: '愿豆包在更大的草地上继续晒太阳。' }],
  },
  {
    slug: 'tuantuan', name: '团团', species: '小猫', kind: '星夜陪伴', years: '2016 - 2024', theme: 'night', coverSymbol: '☾', quote: '她的呼噜声，仍在每一个安静的夜晚。', lightCount: 86,
    timeline: [
      { date: '2016 年冬天', title: '钻进围巾里', body: '第一次抱起她时，只有一条围巾那么轻。' },
      { date: '2020 年雨季', title: '窗边的守夜人', body: '下雨的晚上，她总坐在窗台上，看路灯把水洼照亮。' },
      { date: '2024 年秋天', title: '轻轻说晚安', body: '我们握着她的小爪子，陪她安安静静睡着。' },
    ],
    gallery: [{ symbol: '✦', caption: '看见星星时，她会抬头很久。' }, { symbol: '☾', caption: '团成一团睡在最暖的地方。' }, { symbol: '⌁', caption: '趴在书上，是她的固定位置。' }],
    letter: { subject: '今晚的月亮也很圆。', body: '团团，夜深时我们还是会下意识留一盏小灯。好像你会从走廊那头慢慢走过来，再在脚边团成一团。' },
    tributes: [{ author: '小周', message: '愿她的梦里永远有暖暖的被子。' }, { author: '姐姐', message: '谢谢团团陪了我们那么多个失眠的夜晚。' }],
  },
  {
    slug: 'xiaomai', name: '小麦', species: '小狗', kind: '小小花园', years: '2018 - 2025', theme: 'garden', coverSymbol: '❋', quote: '一只会把快乐叼回家的小狗。', lightCount: 153,
    timeline: [
      { date: '2018 年盛夏', title: '奔向第一片草地', body: '刚学会跑步的小麦，在公园里追着一片叶子跑了很久。' },
      { date: '2022 年春天', title: '认识每一朵花', body: '散步时她总会停下来闻一闻，像在认真打招呼。' },
      { date: '2025 年初春', title: '把花园留在心里', body: '家人把最喜欢的雏菊种在窗下，想念便有了可以照看的地方。' },
    ],
    gallery: [{ symbol: '❋', caption: '春天的第一张合影。' }, { symbol: '♧', caption: '草地永远是她的游乐场。' }, { symbol: '☀', caption: '叼着球跑回来的时候，笑得最开心。' }],
    letter: { subject: '花开的时候，我们都会想起你。', body: '小麦，你把每一次出门都变成了小小的冒险。以后路过有风的草地，我们还会想象你在前面回头等我们。' },
    tributes: [{ author: '外婆', message: '小麦是最会撒娇的小朋友。' }, { author: '小陈', message: '愿她继续在有花的地方奔跑。' }],
  },
]

export function findMemorialExample(slug: string) {
  return memorialExamples.find((example) => example.slug === slug)
}
