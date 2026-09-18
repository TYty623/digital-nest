/** Scene choices and copy live here so the experience can be reskinned without changing behavior. */
export const habitatConfig = {
  storageKey: 'digitalnest:pocket-habitat:v1',
  pets: [
    { id: 'cat', label: '小猫', name: '麦芽', color: '#d7ae78', description: '喜欢窗边的光，也喜欢你刚好有空。' },
    { id: 'dog', label: '小狗', name: '松饼', color: '#b98864', description: '一颗小球，就能让普通的下午变得热闹。' },
    { id: 'rabbit', label: '兔子', name: '糯米', color: '#e7ddcd', description: '轻轻靠近，等它自己向你走来。' },
  ],
  landscapes: [
    { id: 'forest', label: '水下森林', description: '水草、沉木，还有穿过叶子的光。', plant: '#678e70' },
    { id: 'river', label: '溪石浅滩', description: '留出更多水面，让小鱼自在游动。', plant: '#9ba77a' },
    { id: 'garden', label: '苔藓花园', description: '柔软的绿色，慢慢长成一片小森林。', plant: '#90ab69' },
  ],
} as const
