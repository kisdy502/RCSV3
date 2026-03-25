class ToastManager {
    static activeToasts = [];
    static SPACING = 5; // 保持5px间距

    static showToast(content, level = 0) {
        const offsetY = this.calculateOffset();
        const toast = new CommonToast({
            toastContent: content,
            level,
            offsetY
        });
        this.activeToasts.push(toast);
        this.updatePositions();
    }

    static calculateOffset() {
        return this.activeToasts.reduce((sum, toast) => {
            return sum + toast.getHeight() + 20;
        }, 0);
    }

    static removeToast(toast) {
        const index = this.activeToasts.indexOf(toast);
        if (index > -1) {
            this.activeToasts.splice(index, 1);
            this.updatePositions();
        }
    }

    static updatePositions() {
        let currentOffset = 0;
        for (let i = this.activeToasts.length - 1; i >= 0; i--) {
            const toast = this.activeToasts[i];
            toast.updatePosition(currentOffset);
            currentOffset += toast.getHeight() + this.SPACING;
        }
    }
}

class CommonToast {
    static DEFAULT_OPTIONS = {
        toastContent: null,
        level: 0, // 0:默认 1:success 2:warn 3:error
        offsetY: 0
    }

    constructor(options) {
        this.$options = Object.assign({}, CommonToast.DEFAULT_OPTIONS, options);
        this.toastId = 'toast-' + Date.now();
        this.rootElement = null;
        this.create();
    }

    create() {
        const rootElement = document.createElement("div");
        this.rootElement = rootElement;
        // 使用Tailwind CSS类替换Bootstrap类
        rootElement.className = "jzt-toast fixed bottom-0 end-0 p-3 z-[1001]";
        rootElement.style.transform = `translateX(100%) translateY(-${0}px)`;
        rootElement.style.transition = 'transform 0.4s cubic-bezier(0.22, 0.61, 0.36, 1)';

        const toastHtml = `
        <div id="${this.toastId}" class="${this.getToastClasses()}" role="alert" aria-live="assertive" aria-atomic="true">
            <div class="${this.getHeaderClasses()}">
                <i class="${this.getIconClasses()}"></i>
                <strong class="${this.getTitleClasses()}">${this.getTitle()}</strong>
                <button type="button" class="${this.getCloseButtonClasses()}">
                    <span class="sr-only">Close</span>
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                    </svg>
                </button>
            </div>
            <div class="${this.getBodyClasses()}">
                ${this.$options.toastContent}
            </div>
        </div>`;

        rootElement.innerHTML = toastHtml.trim();
        document.body.appendChild(rootElement);

        // 触发滑入动画
        setTimeout(() => {
            rootElement.style.transform = `translateX(0) translateY(-${0}px)`;
        }, 10);

        this.setupEventListeners(rootElement);
    }

    getToastClasses() {
        const baseClasses = "max-w-md rounded-lg shadow-lg border-l-4 ";
        const levelClasses = [
            "bg-blue-50 border-blue-400",    // 默认
            "bg-green-50 border-green-400",  // 成功
            "bg-yellow-50 border-yellow-400", // 警告
            "bg-red-50 border-red-400"       // 错误
        ];
        return baseClasses + levelClasses[this.$options.level];
    }

    getHeaderClasses() {
        return "flex items-center justify-between p-3 border-b border-gray-100";
    }

    getIconClasses() {
        const iconMap = [
            "fas fa-exclamation-circle text-blue-500 text-lg",
            "fas fa-check-circle text-green-500 text-lg",
            "fas fa-exclamation-triangle text-yellow-500 text-lg",
            "fas fa-times-circle text-red-500 text-lg"
        ];
        return iconMap[this.$options.level];
    }

    getTitleClasses() {
        const colorMap = ["text-blue-700", "text-green-700", "text-yellow-700", "text-red-700"];
        return `me-auto font-semibold text-sm ms-2 ${colorMap[this.$options.level]}`;
    }

    getCloseButtonClasses() {
        return "p-1 rounded-full hover:bg-gray-200 transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-gray-300";
    }

    getBodyClasses() {
        const colorMap = ["text-blue-800", "text-green-800", "text-yellow-800", "text-red-800"];
        return `p-3 text-sm ${colorMap[this.$options.level]}`;
    }

    getTitle() {
        return ['提示', '成功', '警告', '错误'][this.$options.level];
    }

    getHeight() {
        return document.getElementById(this.toastId)?.offsetHeight || 80;
    }

    updatePosition(offsetY) {
        const el = document.getElementById(this.toastId)?.parentElement;
        if (el) {
            this.$options.offsetY = offsetY;
            el.style.transform = `translateX(0) translateY(-${offsetY}px)`;
        }
    }

    setupEventListeners(rootElement) {
        // 保存 rootElement 到实例（如果之前已保存，此处可用 this.rootElement）
        const closeButton = rootElement.querySelector('button');
        closeButton.addEventListener('click', () => {
            this.hideAndRemove();
        });

        // 自动隐藏
        setTimeout(() => {
            if (this.rootElement && this.rootElement.parentNode) {
                this.hideAndRemove();
            }
        }, 3000);
    }

    // 新增统一关闭方法
    hideAndRemove() {
        if (!this.rootElement) return;
        // 滑出动画
        this.rootElement.style.transform = `translateX(-100%) translateY(-${this.$options.offsetY}px)`;
        setTimeout(() => {
            ToastManager.removeToast(this);
            this.rootElement.remove();
        }, 400);
    }
}

// 使用示例
// ToastManager.showToast('这是一条普通提示信息', 0);
// ToastManager.showToast('操作成功！', 1);
// ToastManager.showToast('请注意风险', 2);
// ToastManager.showToast('发生错误！', 3);

// (index):1 Uncaught TypeError: Cannot read properties of null (reading 'click')
// at HTMLButtonElement.onclick ((index):1:59)
// onclick	@	(index):1