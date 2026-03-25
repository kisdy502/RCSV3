import request from './request.js'

/**
 * 获取可视化路径
 * @param {Object} params 查询参数 { startNode, endNode }
 */
export function initPose(params) {
    return request({
        url: `api/simulator/init-pose-node`,
        method: 'get',
        params: params
    })
}

export function moveTo(data) {
    return request({
        url: `api/simulator/move_to`,
        method: 'post',
        data: data
    })
}

/**
 * 获取 AGV详细信息
 */
export function getAgvStatus() {
    return request({
        url: `api/simulator/agv`,
        method: 'get'
    })
}