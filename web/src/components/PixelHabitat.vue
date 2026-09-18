<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
const props = defineProps<{ scene: string; light: number; paused: boolean; interacting: boolean }>()
const canvas = ref<HTMLCanvasElement>()
let frame = 0
let timer: ReturnType<typeof setInterval> | undefined
let media: MediaQueryList | undefined
function paint() {
  const ctx = canvas.value?.getContext('2d'); if (!ctx) return
  const c = ctx
  c.imageSmoothingEnabled = false
  const box = (x: number,y: number,w: number,h: number,color: string) => { c.fillStyle=color;c.fillRect(Math.round(x),Math.round(y),w,h) }
  const stars = '#b7b99a'
  box(0,0,384,256,'#172630'); box(0,169,384,87,'#354047')
  for(let y=177;y<256;y+=18) { box(0,y,384,1,'#445052'); for(let x=(y%2)*29;x<384;x+=67) box(x,y,1,18,'#293940') }
  if(props.scene==='forest') {
    box(34,207,323,13,'#655d4e');box(42,220,8,36,'#454741');box(338,220,8,36,'#454741')
    box(43,65,302,143,'#8baba4');box(46,69,296,135,'#203d46');box(49,75,290,122,'#31515a')
    box(49,173,290,24,'#8c866c');box(49,177,290,3,'#aba080')
    for(let i=0;i<64;i++) box(51+(i*37)%282,180+(i*13)%16,3,2,i%2?'#5d6b5f':'#b4ac85')
    for(let n=0;n<17;n++) {
      const x=58+n*16, h=25+(n*19)%60, drift=Math.sin(frame*.055+n)*2
      box(x,178-h,3,h,'#789577')
      for(let j=0;j<4;j++){box(x-9+drift,174-h+j*11,10,5,n%2?'#63866b':'#89a17c');box(x+3,169-h+j*11,9,5,'#a1b58d')}
    }
    box(106,166,39,11,'#566864');box(113,156,25,10,'#6d7d73');box(119,152,16,5,'#8f9b86')
    box(247,165,41,14,'#556762');box(254,151,26,15,'#758275');box(259,147,15,5,'#99a189')
    for(let n=0;n<6;n++) {
      const x=props.interacting ? 156+n*13+Math.sin(frame*.1+n)*5 : 72+((frame*.32+n*41)%235), y=(props.interacting ? 90 : 104+(n%3)*17)+Math.sin(frame*.05+n)*4
      box(x,y,10,5,n%2?'#e2ba82':'#adcabf');box(x+2,y-2,6,9,n%2?'#d4a16c':'#8caea8');box(x-4,y-2,4,9,n%2?'#bb9265':'#74928f');box(x+8,y,1,1,'#233b43')
    }
    box(38,57,312,6,'#c5b48b');box(45,63,297,3,'#eddda9');box(49,72,290,2,'#87aaa7')
    box(52,77,2,118,'#a5c0b0');box(334,78,2,116,'#739d97')
    box(51,80,285,1,'#aec3ab')
    if(props.interacting) for(let n=0;n<8;n++) box(153+n*12,81+(n*7+frame)%18,2,2,'#ead2a3')
    box(15,190,18,19,'#b29879');box(22,161,3,30,'#91a180');box(13,164,10,5,'#789878');box(25,172,9,5,'#a4b38c')
  } else {
    box(32,35,95,110,'#526a73');box(36,39,87,102,'#819a9c');box(39,42,81,96,props.light<60?'#465b74':'#aaaba0')
    box(98,51,12,12,'#e7d2a0');box(101,48,6,18,'#e7d2a0')
    box(40,106,80,31,'#637f79');box(53,96,26,12,'#637f79');box(79,42,4,99,'#354e5a');box(38,92,84,4,'#354e5a')
    box(24,142,111,6,'#9a8c72');box(26,34,8,104,'#60716f');box(123,34,8,104,'#60716f')
    box(42,186,268,35,'#58605c');box(48,182,256,43,'#58605c');box(54,188,244,2,'#797a68');box(54,216,244,2,'#797a68')
    box(191,156,141,44,'#8c745c');box(197,145,129,18,'#a58b6e');box(202,150,119,29,'#c9b696');box(205,155,113,24,'#d7c5a6');box(197,200,8,14,'#645b4e');box(317,200,8,14,'#645b4e')
    const dy=Math.sin(frame*.06)>0?1:0
    box(228,139+dy,67,23,'#c79e70');box(237,132+dy,49,27,'#d9b588');box(215,125+dy,26,27,'#dfbb8d');box(215,116+dy,7,12,'#d9b588');box(234,116+dy,7,12,'#d9b588');box(217,119+dy,3,6,'#a77864');box(236,119+dy,3,6,'#a77864')
    box(218,136+dy,5,1,'#594a42');box(231,136+dy,5,1,'#594a42');box(225,140+dy,3,2,'#976d58');box(228,158,19,5,'#e7c9a0');box(285,149+dy,20,7,'#c69d6d');box(300,140+dy,7,15,'#c69d6d')
    box(150,197,9,9,'#bc9270');box(152,195,5,13,'#d1ac7c')
    if(props.interacting) {box(216,106,4,3,'#d5a793');box(222,106,4,3,'#d5a793');box(216,109,10,3,'#d5a793');box(218,112,6,2,'#d5a793');box(220,114,2,2,'#d5a793')}
    box(331,203,30,5,'#b49b76');box(335,208,22,7,'#928269')
    box(315,55,2,34,'#b7a381');box(306,82,20,8,'#d0b78c');box(309,76,14,7,'#d0b78c');box(309,90,14,3,'#f2d59a')
    box(147,97,31,26,'#9a8569');box(150,100,25,20,'#667971');box(157,106,8,8,'#c2b598')
    // Small domestic details: books, a plant, cushion weave and the cat's markings.
    box(192,73,65,4,'#8c7c63');box(198,59,7,14,'#8da29a');box(207,56,6,17,'#bca27b');box(215,62,9,11,'#a18572')
    box(239,63,11,10,'#bb9f7d');box(243,48,2,15,'#91a885');box(237,50,7,4,'#8ba180');box(245,54,7,4,'#a6b797')
    for(let n=0;n<12;n++) box(211+n*8,171,3,1,'#b7a686')
    for(let n=0;n<4;n++) box(249+n*8,137+dy,3,6,'#bd976e')
    box(224,127+dy,2,4,'#bd976e');box(230,127+dy,2,4,'#bd976e')
    box(66,122,20,17,'#ad9475');box(74,104,3,18,'#85997c');box(67,107,8,4,'#8ba17e');box(77,113,8,4,'#a1af8c')
  }
  box(8,248,1,1,stars)
  c.fillStyle=`rgba(9,18,32,${(100-props.light)/160})`;c.fillRect(0,0,384,256)
}
watch(() => [props.scene, props.light, props.interacting], paint)
onMounted(() => { media=matchMedia('(prefers-reduced-motion: reduce)'); paint();timer=setInterval(()=>{if(!props.paused && !media?.matches && !document.hidden){frame++;paint()}},100) })
onUnmounted(()=>clearInterval(timer))
defineExpose({ getCanvas: () => canvas.value })
</script>
<template><canvas ref="canvas" width="384" height="256" class="pixel-scene" role="img" :aria-label="scene === 'forest' ? '原创像素生态缸，小鱼在水草间慢慢游动' : '原创像素小屋，小猫在窗边的软垫上打盹'"></canvas></template>
<style scoped>.pixel-scene{display:block;width:100%;height:100%;image-rendering:pixelated;object-fit:contain}</style>
