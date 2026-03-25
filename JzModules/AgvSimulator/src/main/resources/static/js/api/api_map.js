import request from './request.js'

/**
 * 获取地图详细信息
 */
export function getMapDetailInfo() {
    return request({
        url: `api/map/detail`,
        method: 'get'
    })
}

/**
 * 获取地图图片（二进制数据）
 * 注：公共 request 需支持 responseType: 'arraybuffer'
 */
export function getMapImage() {
    return request({
        url: `api/map/image`,
        method: 'get',
        responseType: 'arraybuffer'
    })
}
