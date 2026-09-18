const dialog=document.querySelector('dialog');
let opener;
document.querySelectorAll('[data-create]').forEach(button=>button.addEventListener('click',()=>{opener=button;dialog.showModal()}));
dialog?.querySelector('.close').addEventListener('click',()=>dialog.close());
dialog?.addEventListener('close',()=>opener?.focus());
let photoUrl;
const photoInput=dialog?.querySelector('[type=file]');
photoInput?.addEventListener('change',()=>{const file=photoInput.files[0];if(photoUrl)URL.revokeObjectURL(photoUrl);photoUrl=file?URL.createObjectURL(file):undefined});
dialog?.querySelector('form').addEventListener('submit',event=>{event.preventDefault();const result=dialog.querySelector('.demo-result');result.hidden=false;result.querySelector('h3').textContent=dialog.querySelector('[name=pet]').value;const img=result.querySelector('img');img.src=photoUrl||'assets/dog.svg';result.scrollIntoView({block:'nearest',behavior:'smooth'})});
document.querySelectorAll('[data-light]').forEach(button=>button.addEventListener('click',()=>{const pressed=button.getAttribute('aria-pressed')==='true';button.setAttribute('aria-pressed',String(!pressed));button.textContent=pressed?'为 TA 留一盏灯 ↗':'已点亮 · 想念被轻轻收好'}));

