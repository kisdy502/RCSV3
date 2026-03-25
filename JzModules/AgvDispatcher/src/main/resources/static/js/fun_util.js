/**
 * 防抖,多次调用，执行的是最后一次，防止多次执行同一个函数造成的卡顿，会丢弃前面的请求
 */
function debounce(func, wait) {
    let timeout;
    return function () {
        const context = this, args = arguments
        clearTimeout(timeout);
        timeout = setTimeout(function () {
            func.apply(context, args)
        }, wait);

    }
}

/**
 * 节流，多次调用，只执行了第一次，用途防止多次点击，重复提交
 * @param func
 * @param limit
 * @returns {(function(): void)|*}
 */
function throttle(func, limit) {
    let inThrottle;
    return function () {
        const context = this, args = arguments;
        if (!inThrottle) {
            func.apply(context, args);
            inThrottle = true;
            setTimeout(function () {
                inThrottle = false;
            }, limit)
        }
    }

}

