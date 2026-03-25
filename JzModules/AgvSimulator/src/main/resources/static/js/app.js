import {createApp, ref, reactive, onMounted, onUnmounted} from '/js/lib/vue3.3.7.js';

import {useAgvPathPlanning} from './useAgvPathPlanning.js'

const app = createApp({
    setup() {
        const {
            // formData,
            // mapInfoDisplay,
            // planningResultDisplay,
            stationNodes,
//            agvResponse,
            zoomState,
            loadMapInfo,
            // sendTaskDispatch,
            // visualizeRoute,
            clearRoute,
            resizeCanvas,
            handleZoom,
            handleMouseDown,
            handleMouseMove,
            handleMouseUp,
            handleCanvasClick,
            handleDynamicCanvasClick,
            handleContextMenu,
            handleDynamicContextMenu,
            handleDynamicWheel,
            handleDynamicMouseDown,
            handleDynamicMouseMove,
            handleDynamicMouseUp,
            initialize,
            scanDisplayConfig,
//            toggleLaserScan,
//            clearLaserScanData,
            drawDynamicMap,
//            laserScanManager
            contextMenu,
            handleMoveToNode,
            handleSetAsStart,
            handleCloseMenu,
        } = useAgvPathPlanning()

        // 在组件挂载时初始化
        initialize()

        // 返回模板中需要使用的所有内容
        return {
            // formData,
            // mapInfoDisplay,
            // planningResultDisplay,
            stationNodes,
//            agvResponse,
            zoomState,
            loadMapInfo,
            // sendTaskDispatch,
            // visualizeRoute,
            clearRoute,
            resizeCanvas,
            handleZoom,
            handleMouseDown,
            handleMouseMove,
            handleMouseUp,
            handleCanvasClick,
            handleDynamicCanvasClick,
            handleDynamicContextMenu,
            handleDynamicWheel,
            handleDynamicMouseDown,
            handleDynamicMouseMove,
            handleDynamicMouseUp,
            handleContextMenu,
            scanDisplayConfig,
//            toggleLaserScan,
//            clearLaserScanData,
            drawDynamicMap,
//            laserScanManager
            contextMenu,
            handleMoveToNode,
            handleSetAsStart,
            handleCloseMenu,
        }
    },
})

app.mount('#app')