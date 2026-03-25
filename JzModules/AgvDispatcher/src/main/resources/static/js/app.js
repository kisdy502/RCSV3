import { createApp, ref, reactive, onMounted, onUnmounted } from '/js/vue3.3.7.js';

import { useAgvPathPlanning } from './useAgvPathPlanning.js'

const app = createApp({
    setup() {
        const {
            formData,
            mapInfoDisplay,
            planningResultDisplay,
            stationNodes,
            nodes,
            agvList,
            zoomState,
            loadMapInfo,
            sendTaskDispatch,
            visualizeRoute,
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
            initialize
        } = useAgvPathPlanning()

        // 在组件挂载时初始化
        initialize()

        // 返回模板中需要使用的所有内容
        return {
            formData,
            mapInfoDisplay,
            planningResultDisplay,
            stationNodes,
            nodes,
            agvList,
            zoomState,
            loadMapInfo,
            sendTaskDispatch,
            visualizeRoute,
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
        }
    }
})

app.mount('#app')