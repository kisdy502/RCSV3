// 创建axios实例
const service = axios.create({
    baseURL: '', // api的base_url
    timeout: 10000 // 请求超时时间
})

// 引入UI提示库（例如Element UI的Message）
// import {Message} from 'element-ui'

// request拦截器
service.interceptors.request.use(config => {
    // 可在此统一添加token等请求头
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config
}, error => {
    // 请求配置错误，直接拒绝
    return Promise.reject(error)
})

// response拦截器
service.interceptors.response.use(
    response => {
        // 状态码为2xx，进入这里
        // 你可以根据后端数据协议，在此处对响应数据做进一步处理
        // 例如，如果后端返回 { code: 200, data: ..., message: 'success' }，可以在此判断code非200的情况
        return response
    },
    error => {
        // 网络错误或状态码非2xx，进入这里
        let userFriendlyMessage = '网络或服务异常，请稍后重试'

        if (error.response) {
            // 请求成功发出，服务器也响应了，但状态码不在2xx范围内
            const status = error.response.status
            const serverMessage = error.response.data?.message

            switch (status) {
                case 400:
                    userFriendlyMessage = serverMessage || '请求参数错误'
                    break
                case 401:
                    userFriendlyMessage = serverMessage || '登录已过期，请重新登录'
                    // 可在此触发跳转登录页
                    // router.push('/login')
                    break
                case 403:
                    userFriendlyMessage = serverMessage || '没有权限执行此操作'
                    break
                case 404:
                    userFriendlyMessage = serverMessage || '请求的资源不存在'
                    break
                case 500:
                case 502:
                case 503:
                    userFriendlyMessage = serverMessage || '服务器开小差了，请稍后再试'
                    break
                default:
                    userFriendlyMessage = serverMessage || `请求失败 (${status})`
            }
        } else if (error.request) {
            // 请求已发出但没有收到响应（网络断开、超时、跨域问题等）
            if (error.code === 'ECONNABORTED' && error.message.includes('timeout')) {
                userFriendlyMessage = '请求超时，请检查网络或联系管理员'
            } else {
                userFriendlyMessage = '网络异常，请检查您的网络连接'
            }
        } else {
            // 在设置请求时触发了一些错误（如请求配置错误）
            userFriendlyMessage = '请求发送失败'
        }

        // 统一提示错误信息（避免重复提示）
        if (error.config?.showError !== false) {
            ToastManager.showToast(userFriendlyMessage,3)
            // Message({
            //     message: userFriendlyMessage,
            //     type: 'error',
            //     duration: 5 * 1000 // 显示5秒
            // })
        }

        // 可选择将错误上报到监控系统
        // reportError(error)

        // 将错误继续抛出，业务代码的catch块仍能捕获到
        return Promise.reject({
            ...error,
            userFriendlyMessage // 可选的，将友好信息挂载到错误对象上
        })
    }
)

export default service