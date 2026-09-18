export type MemoryInterviewPrompt = {
  key: string
  label: string
  question: string
  helper: string
  acceptedNow: readonly ('TEXT' | 'AUDIO' | 'PHOTO')[]
}

/** Questions guide recall; no generated answer is ever written back as a memory. */
export const memoryInterviewPrompts: readonly MemoryInterviewPrompt[] = [
  { key: 'HOME_ACTION', label: '一个动作', question: 'TA 听见你回家时，身体会先做什么？', helper: '可以写耳朵、尾巴、脚步，或它跑向你的路线。', acceptedNow: ['TEXT'] },
  { key: 'FAMILIAR_SOUND', label: '一种声音', question: '闭上眼睛，最先想起的是哪种声音？', helper: '项圈、喝水、爪子、呼噜、叫声，越具体越好。', acceptedNow: ['TEXT', 'AUDIO'] },
  { key: 'OWNED_PLACE', label: '一个位置', question: '家里哪一小块地方看起来永远属于 TA？', helper: '窗边、门垫、沙发缝或阳光刚好落下的地方。', acceptedNow: ['TEXT', 'PHOTO'] },
  { key: 'SCENT', label: '一种气味', question: '哪种气味会让你突然想起 TA？', helper: '雨后、晒过的被子、零食袋或散步的草地。', acceptedNow: ['TEXT'] },
  { key: 'KEEPSAKE', label: '一件旧物', question: '哪件东西你一直没有舍得收起来？', helper: '说说它现在在哪里，和它留下的使用痕迹。', acceptedNow: ['TEXT', 'PHOTO'] },
  { key: 'SIGNATURE_QUIRK', label: '一个小动作', question: '什么小动作最能证明它就是它？', helper: '不是性格标签，是只有家人才认得出的细节。', acceptedNow: ['TEXT', 'PHOTO'] },
  { key: 'FAMILY_RELATION', label: '家里的人', question: 'TA 对家里不同的人分别是什么态度？', helper: '可以只写称呼，不需要披露真实姓名。', acceptedNow: ['TEXT'] },
  { key: 'MISCHIEF', label: '一场淘气', question: '哪次闯祸后来变成了全家的笑话？', helper: '只写你愿意保存的部分，不必把回忆写完整。', acceptedNow: ['TEXT', 'PHOTO'] },
  { key: 'COMFORT', label: '一次安慰', question: '你难过时，TA 会怎样陪着你？', helper: '也许只是靠近、看一眼，或安静待在同一间房。', acceptedNow: ['TEXT'] },
  { key: 'THANKS', label: '一句谢谢', question: '如果只说一句谢谢，你最想谢谢什么？', helper: '没有标准答案；一两句话也足够。', acceptedNow: ['TEXT'] },
] as const

export function memoryInterviewLabel(key: string) {
  return memoryInterviewPrompts.find((prompt) => prompt.key === key)?.question
    ?? legacyInterviewLabels[key]
    ?? key
}

const legacyInterviewLabels: Record<string, string> = {
  FIRST_MEETING: '第一次见面', NICKNAME: '家人才懂的昵称', HABIT: '最熟悉的小习惯',
  HAPPIEST: '最快乐的时光', TAUGHT_ME: 'TA 教会我的事', LAST_WORDS: '最想对 TA 说',
}
