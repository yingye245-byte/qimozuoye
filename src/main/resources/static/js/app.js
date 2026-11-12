/* 校园二手闲置交易平台 - 前端小工具集合 */

/* 图片切换（详情页缩略图） */
function switchCover(src) {
    var main = document.getElementById('mainCover');
    if (main) main.src = src;
}

/* 支付倒计时：data-remain 秒，到 0 后自动刷新页面（后端此时已完成取消） */
function startCountdown() {
    var els = document.querySelectorAll('[data-remain]');
    Array.prototype.forEach.call(els, function (el) {
        var left = parseInt(el.getAttribute('data-remain'), 10);
        if (isNaN(left) || left <= 0) {
            el.textContent = '已超时';
            return;
        }

        function render() {
            var m = Math.floor(left / 60);
            var s = left % 60;
            el.textContent = m + ' 分 ' + (s < 10 ? '0' + s : s) + ' 秒';
        }

        render();
        var timer = setInterval(function () {
            left--;
            if (left <= 0) {
                clearInterval(timer);
                el.textContent = '已超时';
                setTimeout(function () { location.reload(); }, 1500);
                return;
            }
            render();
        }, 1000);
    });
}

/* 图片上传：选择文件后 POST 到 /upload/image，把返回路径填进隐藏域并预览 */
function bindImageUpload(inputEl, hiddenId, previewId) {
    if (!inputEl) return;
    inputEl.addEventListener('change', function () {
        var file = inputEl.files && inputEl.files[0];
        if (!file) return;
        var fd = new FormData();
        fd.append('file', file);
        var token = document.querySelector('meta[name=_csrf]');
        fetch('/upload/image', {
            method: 'POST',
            body: fd,
            headers: token ? { 'X-CSRF-TOKEN': token.getAttribute('content') } : {}
        })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200 && res.data) {
                    document.getElementById(hiddenId).value = res.data;
                    var img = document.getElementById(previewId);
                    if (img) { img.src = res.data; img.style.display = 'block'; }
                } else {
                    alert(res.message || '上传失败');
                }
            })
            .catch(function () { alert('上传失败，请重试'); });
    });
}

/* 发布页：最多 3 张图片的上传绑定 */
function initPublishUpload() {
    for (var i = 1; i <= 3; i++) {
        bindImageUpload(document.getElementById('imgInput' + i), 'imgUrl' + i, 'imgPreview' + i);
    }
}

/* 发布页：隐藏域里的图片路径合并成封面 + 多图字段 */
function collectPublishImages() {
    var urls = [];
    for (var i = 1; i <= 3; i++) {
        var el = document.getElementById('imgUrl' + i);
        if (el && el.value) urls.push(el.value);
    }
    var cover = document.getElementById('coverField');
    var imgs = document.getElementById('imagesField');
    if (cover) cover.value = urls.length ? urls[0] : '';
    if (imgs) imgs.value = urls.join(',');
    return true;
}

document.addEventListener('DOMContentLoaded', function () {
    startCountdown();
    if (document.getElementById('imgInput1')) {
        initPublishUpload();
        var form = document.getElementById('publishForm');
        if (form) form.addEventListener('submit', collectPublishImages);
    }
});