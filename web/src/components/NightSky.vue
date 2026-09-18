<template>
  <div class="night-sky" role="img" aria-label="小狗坐在月亮与星光下的山坡上">
    <div class="sky-stars"></div>
    <div class="sky-orbit"></div>
    <div class="shooting-star" aria-hidden="true"></div>
    <div class="sky-moon"></div>
    <svg viewBox="0 0 480 280" aria-hidden="true">
      <path d="M0 145Q200 20 480 166v114H0" fill="#243642" />
      <path d="M0 214Q260 100 480 211v69H0" fill="#1a2b35" />
      <path
        class="sky-friend"
        d="M235 180q-21-38-16-64l-14-21 13-22 29 12 22-16 15 29-16 17q17 24 8 61l21 15h-60z"
        fill="#bdad8c"
      />
      <path
        class="sky-tail"
        d="M274 169q53-8 51-40"
        fill="none"
        stroke="#bdad8c"
        stroke-width="13"
        stroke-linecap="round"
      />
      <path d="M0 238q264-86 480-4v46H0" fill="#101e28" />
    </svg>
    <p>你是我见过，最温柔的星光。</p>
  </div>
</template>
<style scoped>
.night-sky {
  position: relative;
  height: 490px;
  border: 1px solid var(--border);
  border-radius: 250px 250px 0 0;
  overflow: hidden;
  background: radial-gradient(ellipse at 50% 20%, #344454, transparent 70%);
}
.sky-moon {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: #e2d3ae;
  position: absolute;
  left: calc(50% - 60px);
  top: 72px;
  box-shadow: 0 0 70px rgb(226 211 174 / 24%);
  animation: moon-breathe 7s ease-in-out infinite;
}
.sky-moon:after {
  content: '';
  position: absolute;
  inset: -9px 25px 13px -9px;
  background: #2b3b4b;
  border-radius: 50%;
}
.sky-stars {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle at 20% 20%, #d8be88 1px, transparent 2px),
    radial-gradient(circle at 80% 40%, #d8be88 1px, transparent 2px),
    radial-gradient(circle at 67% 12%, #d8be88 1px, transparent 2px),
    radial-gradient(circle at 25% 56%, #d8be88 1px, transparent 2px),
    radial-gradient(circle at 75% 66%, #d8be88 1px, transparent 2px);
  animation: stars-breathe 5.5s ease-in-out infinite alternate;
}
.sky-orbit {
  position: absolute;
  width: 340px;
  height: 340px;
  border: 1px solid #d8be8820;
  border-radius: 50%;
  top: 30px;
  left: calc(50% - 170px);
  animation: orbit-drift 28s linear infinite;
}
.shooting-star {
  position: absolute;
  z-index: 2;
  top: 92px;
  left: 17%;
  width: 72px;
  height: 1px;
  opacity: 0;
  background: linear-gradient(90deg, transparent, #f4e2b8);
  transform: rotate(-24deg) translateX(-70px);
  animation: star-crossing 8s ease-in-out infinite 2.8s;
}
.sky-friend {
  transform-box: fill-box;
  transform-origin: center bottom;
  animation: friend-breathe 4.8s ease-in-out infinite;
}
.sky-tail {
  transform-box: fill-box;
  transform-origin: left center;
  animation: tail-wag 5.2s ease-in-out infinite;
}
svg {
  position: absolute;
  bottom: 0;
  width: 100%;
  height: 280px;
}
p {
  position: absolute;
  bottom: 25px;
  left: 0;
  right: 0;
  text-align: center;
  font: 14px var(--font-serif);
  letter-spacing: 0.12em;
  color: #efe9dc;
}
@keyframes moon-breathe {
  50% { transform: translateY(-5px); box-shadow: 0 0 90px rgb(226 211 174 / 34%); }
}
@keyframes stars-breathe { to { opacity: .48; } }
@keyframes orbit-drift { to { transform: rotate(360deg); } }
@keyframes friend-breathe { 50% { transform: scaleY(1.015) translateY(-1px); } }
@keyframes tail-wag { 0%, 82%, 100% { transform: rotate(0); } 88% { transform: rotate(-7deg); } 94% { transform: rotate(4deg); } }
@keyframes star-crossing { 0%, 62% { opacity: 0; transform: rotate(-24deg) translateX(-70px); } 68% { opacity: .9; } 78%, 100% { opacity: 0; transform: rotate(-24deg) translateX(250px); } }
@media (prefers-reduced-motion: reduce) {
  .sky-moon, .sky-stars, .sky-orbit, .shooting-star, .sky-friend, .sky-tail { animation: none; }
}
@media (max-width: 700px) {
  .night-sky {
    height: 350px;
    max-width: 380px;
    width: 100%;
    margin: auto;
  }
  svg {
    height: 215px;
  }
  .sky-moon {
    top: 42px;
    width: 95px;
    height: 95px;
    left: calc(50% - 47px);
  }
}
</style>
