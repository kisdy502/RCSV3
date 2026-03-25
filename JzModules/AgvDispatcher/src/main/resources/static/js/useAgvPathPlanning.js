import {createApp, ref, reactive, onMounted, onUnmounted, computed} from '/js/vue3.3.7.js';
import {MapDrawer} from "./utils/map_drawer.js";

const API_BASE = 'api/dispatch/path'
const API_BASE2 = 'api/map'

export function useAgvPathPlanning() {
    // 响应式数据
    const formData = reactive({
        startNode: '',
        endNode: '',
        agvId: '',
        algorithm: 'astar'
    })

    //右键菜单相关状态
    const contextMenu = reactive({
        visible: false,
        x: 0,
        y: 0,
        targetType: null, // 'node' 或 'edge'
        targetData: null, // 点击的目标数据
        menuWidth: 180, // 菜单宽度
        menuHeight: 100 // 菜单高度（根据菜单项计算）
    })

    const mapInfo = ref(null)
    const mapName = ref('')
    const edges = ref([])
    const currentRoute = ref(null)
    const agvStatusList = ref([])
    const dataView = ref(null)

    const nodes = ref([])
    const agvList = ref([])

    const mapDrawer = ref(null);
    const agvDrawer = ref(null);
    /*为canvas设置点击事件和右键菜单事件*/
    const selectedNodeInfo = ref(null) // 存储选中的站点信息
    const showNodeInfo = ref(false) // 控制显示

    // 计算属性
    const filteredStationNodes = computed(() => {
        return nodes.value.filter(node => node.type === 'STATION')
    })

    const mapInfoDisplay = ref('')
    const planningResultDisplay = ref('')

    // 定时器引用
    const agvUpdateInterval = ref(null)

    // 画布控制状态
    const canvasState = reactive({
        isMouseDown: false,
        lastMouseX: 0,
        lastMouseY: 0,
        offset: {x: 0, y: 0}
    })

    // 响应式数据
    const zoomState = reactive({
        level: 1.0,
        min: 0.5,
        max: 8,
        smoothFactor: 0.04, // 平滑系数，值越小越平滑
        wheelAccumulator: 0, // 滚轮累积量
        threshold: 100 // 触发阈值
    });

    // 计算属性：过滤出站点类型的节点
    const stationNodes = computed(() => {
        return nodes.value.filter(node => node.type === 'STATION')
    })

    // 方法定义
    const loadMapInfo = async () => {
        try {
            const response = await axios.get(`${API_BASE}/map/info`)
            const result = response.data
            if (result.code === 0 && result.data) {
                mapInfoDisplay.value = formatJson(result.data)
                mapInfo.value = result.data.mapInfo
                mapName.value = result.data.mapName
                nodes.value = result.data.nodes || []
                edges.value = result.data.edges || []
            } else {
                displayError('mapInfo', new Error(result.msg || '获取地图信息失败'))
            }
        } catch (error) {
            displayError('mapInfo', error)
        }
    }

    const loadMapImage = async () => {
        try {
            const response = await fetch(`${API_BASE2}/image`)
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`)
            }
            const arrayBuffer = await response.arrayBuffer()
            dataView.value = new DataView(arrayBuffer)
        } catch (error) {
            displayError('mapInfo', error)
        }
    }

    const sendTaskDispatch = async () => {
        const request = {
            taskName: `AgvTask_${formData.startNode}->${formData.endNode}`,
            startNode: formData.startNode,
            endNode: formData.endNode,
            agvId: formData.agvId
        }

        try {
            const response = await axios.post(`${API_BASE}/task/send`, request)
            const result = response.data
            if (result.code === 0 && result.data) {
                planningResultDisplay.value = formatJson(result.data)
            } else {
                displayError('planningResult', new Error(result.msg || '任务调度失败'))
            }
        } catch (error) {
            displayError('planningResult', error)
        }
    }

    const visualizeRoute = async () => {
        try {
            const response = await axios.get(`${API_BASE}/route/visualize`, {
                params: {
                    startNode: formData.startNode,
                    endNode: formData.endNode
                }
            })

            const result = response.data
            if (result.code === 0 && result.data) {
                const routeData = result.data
                if (routeData.route) {
                    currentRoute.value = {
                        nodes: routeData.route.nodeSequence,
                        edges: routeData.route.edgeSequence || [],
                        details: routeData.route
                    }
                    drawStaticMap()
                    drawDynamicMap()
                }
            } else {
                console.error('可视化路线失败:', result.msg)
            }
        } catch (error) {
            console.error('可视化路线失败', error)
        }
    }

    const clearRoute = () => {
        currentRoute.value = null
        drawStaticMap()
        drawDynamicMap()
    }

    const loadAgvPositions = async () => {
        try {
            const response = await axios.get(`${API_BASE}/agvs`)
            const result = response.data
            if (result.code === 0) {
                agvStatusList.value = result.data || []

                agvList.value = agvStatusList.value.map(agv => agv.agvId)
                // 设置默认选中的AGV
                if (agvList.value.length > 0 && !formData.agvId) {
                    formData.agvId = agvList.value[0]
                }

                if (nodes.value.length > 0) {
                    drawDynamicMap()
                }
            } else {
                console.error('获取AGV位置失败:', result.msg)
            }
        } catch (error) {
            console.error('获取AGV位置失败', error)
        }
    }

    const startAgvPositionUpdates = (interval = 3000) => {
        if (agvUpdateInterval.value) {
            clearInterval(agvUpdateInterval.value)
        }

        loadAgvPositions()
        agvUpdateInterval.value = setInterval(loadAgvPositions, interval)
    }

    const stopAgvPositionUpdates = () => {
        if (agvUpdateInterval.value) {
            clearInterval(agvUpdateInterval.value)
            agvUpdateInterval.value = null
        }
    }

    const calculateBounds = (canvas) => {
        return {minX: 0, maxX: mapInfo.value.width, minY: 0, maxY: mapInfo.value.height};
    };

    // 绘制静态层（完整重绘）
    const drawStaticMap = () => {
        const canvas = document.getElementById('mapCanvasStatic')
        if (!canvas) return
        const ctx = canvas.getContext('2d', {alpha: true, antialias: true})
        if (!dataView.value) return

        // 初始化或更新绘制器
        if (!mapDrawer.value) {
            mapDrawer.value = new MapDrawer(ctx, mapInfo.value, zoomState.level);
        } else {
            mapDrawer.value.setContext(ctx);
            mapDrawer.value.setZoomLevel(zoomState.level);
            mapDrawer.value.setMapInfo(mapInfo.value);
        }

        mapDrawer.value.setCanvas(canvas);
        mapDrawer.value.setBounds(calculateBounds(canvas));

        if (nodes.value.length === 0) {
            mapDrawer.value.drawPlaceholder();
            return
        }

        // 使用 MapDrawer 绘制地图
        mapDrawer.value.drawMap(
            dataView.value,
            nodes.value,
            edges.value,
            currentRoute.value
        );
    }

    // 绘制动态层（仅 AGV）
    const drawDynamicMap = () => {
        const canvas = document.getElementById('mapCanvasDynamic')
        if (!canvas) return
        const ctx = canvas.getContext('2d', {alpha: true, antialias: true})
        ctx.clearRect(0, 0, canvas.width, canvas.height)

        // 初始化或更新AGV绘制器
        if (!agvDrawer.value) {
            agvDrawer.value = new MapDrawer(ctx, mapInfo.value, zoomState.level);
        } else {
            agvDrawer.value.setContext(ctx);
            agvDrawer.value.setZoomLevel(zoomState.level);
            agvDrawer.value.setMapInfo(mapInfo.value);
        }

        agvDrawer.value.setCanvas(canvas);
        agvDrawer.value.setNodes(nodes.value);
        agvDrawer.value.setAgvStatusList(agvStatusList.value);

        // 绘制AGV
        agvDrawer.value.drawAgvStatusList();
    }

    const resizeCanvas = () => {
        const staticCanvas = document.getElementById('mapCanvasStatic')
        const dynamicCanvas = document.getElementById('mapCanvasDynamic')
        const container = document.getElementById('mapRoot')
        if (staticCanvas && dynamicCanvas && container) {
            const w = container.clientWidth
            const h = container.clientHeight
            staticCanvas.width = w
            staticCanvas.height = h
            dynamicCanvas.width = w
            dynamicCanvas.height = h

            drawStaticMap()  // 静态层重绘
        }
    }


    const handleMouseDown = (event) => {
        if (event.button === 0) {
            canvasState.isMouseDown = true
            canvasState.lastMouseX = event.clientX
            canvasState.lastMouseY = event.clientY
        }
    }

    const handleMouseMove = (event) => {
        if (canvasState.isMouseDown) {
            const deltaX = event.clientX - canvasState.lastMouseX
            const deltaY = event.clientY - canvasState.lastMouseY
            // 添加阈值判断，例如最小移动16像素才触发重绘
            const threshold = 16;
            if (Math.abs(deltaX) > threshold || Math.abs(deltaY) > threshold) {
                canvasState.offset.x += deltaX
                canvasState.offset.y += deltaY
                drawStaticMap();
            }
            // 无论是否触发重绘，都要更新最后记录的鼠标位置
            canvasState.lastMouseX = event.clientX
            canvasState.lastMouseY = event.clientY
        }
    }

    const handleMouseUp = () => {
        canvasState.isMouseDown = false
    }
    // 工具方法
    const displayError = (elementId, error) => {
        if (elementId === 'mapInfo') {
            mapInfoDisplay.value = `<div class="error">错误: ${error.message}</div>`
        } else if (elementId === 'planningResult') {
            planningResultDisplay.value = `<div class="error">错误: ${error.message}</div>`
        }
    }

    const formatJson = (data) => {
        return '<pre>' + JSON.stringify(data, null, 2) + '</pre>'
    }

    const debouncedResize = debounce(resizeCanvas, 300)

    // 新增：处理API响应的辅助函数
    const handleApiResponse = (response, successCallback) => {
        const result = response.data
        if (result.code === 0) {
            if (successCallback) {
                successCallback(result.data, result.msg)
            }
            return true
        } else {
            console.error(`API调用失败: ${result.msg} (code: ${result.code})`)
            return false
        }
    }

    // 初始化
    const initialize = async () => {
        await loadMapImage()
        await loadMapInfo()
        startAgvPositionUpdates(1300)
        resizeCanvas()
    }

    // 生命周期
    onMounted(() => {
        window.addEventListener('resize', debouncedResize)
    })

    onUnmounted(() => {
        stopAgvPositionUpdates()
        window.removeEventListener('resize', debouncedResize)
    })

    //上层动态canvas鼠标滚动事件
    const handleDynamicWheel = (event) => {
        if (detectAgvAtFromEvent(event)) {
            // 未来可在此处理 AGV 专用缩放逻辑（如单独放大 AGV 图标）
            event.preventDefault()
            return
        }
        // 未命中 AGV → 转发给静态 Canvas 的缩放处理函数
        handleZoom(event)
    }

    // 优化后的缩放处理函数
    const handleZoom = (event) => {
        event.preventDefault();

        // 累积滚轮事件，实现更精细的控制
        zoomState.wheelAccumulator += Math.abs(event.deltaY);

        // 只有当累积滚动量超过阈值时才执行缩放
        if (zoomState.wheelAccumulator >= zoomState.threshold) {
            // 根据滚动方向应用缩放
            const zoomIntensity = 0.2; // 缩放强度
            const wheelDirection = event.deltaY > 0 ? -1 : 1;
            const newZoom = zoomState.level * (1 + wheelDirection * zoomIntensity);

            // 应用缩放限制
            zoomState.level = Math.max(zoomState.min, Math.min(newZoom, zoomState.max));
            zoomState.wheelAccumulator = 0; // 重置累积器

            console.log(`缩放级别: ${zoomState.level.toFixed(2)}x`);
            //drawMap();
            drawStaticMap();
            drawDynamicMap()
        }
    };

    const handleDynamicMouseDown = (event) => {
        if (detectAgvAtFromEvent(event)) {
            // 可能用于选中 AGV 并单独拖动 AGV（若业务需要）
            event.preventDefault()
            return
        }
        // 未命中 AGV → 启动地图拖拽
        handleMouseDown(event)
    }

    const handleDynamicMouseMove = (event) => {
        if (canvasState.isMouseDown) {
            // 当前处于地图拖拽状态，直接转发给静态层拖拽逻辑
            handleMouseMove(event)
        } else {
            // 非拖拽状态：可在此做 AGV 悬停提示等
        }
    }

    const handleDynamicMouseUp = (event) => {
        handleMouseUp(event) // 无论是否命中 AGV，都应结束拖拽状态
    }


    // 左键点击事件
    const handleDynamicCanvasClick = (event) => {
        const canvas = event.target
        const rect = canvas.getBoundingClientRect()
        const scaleX = canvas.width / rect.width
        const scaleY = canvas.height / rect.height
        const canvasX = (event.clientX - rect.left) * scaleX
        const canvasY = (event.clientY - rect.top) * scaleY

        if (!agvStatusList.value || agvStatusList.value.length === 0) return false
        // 计算 AGV 屏幕坐标（与 drawAgvPosition 保持一致）
        let screenX, screenY, agvAngle
        agvStatusList.value.forEach(agv => {
            // 计算 AGV 屏幕坐标（与 drawAgvPosition 完全一致）
            if (agv.currentPosition?.x !== undefined) {
                const agvPos = convertRobotPoseToWeb(mapInfo.value, agv.currentPosition.x, agv.currentPosition.y, agv.currentPosition?.theta || 0)
                screenX = agvPos.pixelX * zoomState.level
                screenY = agvPos.pixelY * zoomState.level
                agvAngle = agvPos.webAngle
            } else if (agv.currentNodeId) {
                const node = nodes.value.find(n => n.id === agv.currentNodeId)
                if (node) {
                    const agvPos = convertRobotPoseToWeb(mapInfo.value, node.x, node.y, node.theta)
                    screenX = agvPos.pixelX * zoomState.level
                    screenY = agvPos.pixelY * zoomState.level
                    agvAngle = agvPos.webAngle
                } else {
                    return
                }
            } else {
                return
            }

            // 简单命中检测：以中心点为圆心，半径 30px（根据 AGV 图标大小调整）
            const hitRadius = 30
            const distance = Math.hypot(canvasX - screenX, canvasY - screenY)
            if (distance <= hitRadius) {
                // 触发 AGV 点击回调，可自定义事件或直接 console
                console.log(`AGV点击: ${agv.agvId}, 状态: ${agv.agvState}, 电量: ${agv.batteryLevel}%`)
                // 可将选中 AGV 信息存入响应式变量供外部使用
                // selectedAgvInfo.value = { ... }
            }

            // 没有点击到AGV，将事件转发给静态canvas
            const staticCanvas = document.getElementById('mapCanvasStatic')
            if (staticCanvas) {
                // 创建新的点击事件并触发
                const newEvent = new MouseEvent('click', {
                    clientX: event.clientX,
                    clientY: event.clientY,
                    button: event.button,
                    buttons: event.buttons,
                    ctrlKey: event.ctrlKey,
                    shiftKey: event.shiftKey,
                    altKey: event.altKey,
                    metaKey: event.metaKey
                })
                staticCanvas.dispatchEvent(newEvent)
            }
        })
    }

    /*为canvas设置点击事件和右键菜单事件*/
    const handleCanvasClick = (event) => {
        const canvas = event.target
        const rect = canvas.getBoundingClientRect()
        const scaleX = canvas.width / rect.width
        const scaleY = canvas.height / rect.height
        const canvasX = (event.clientX - rect.left) * scaleX
        const canvasY = (event.clientY - rect.top) * scaleY

        // 先检测是否点击到节点（优先检测，因为节点区域较小）
        const clickedNode = detectNodeAt(canvasX, canvasY)
        if (clickedNode) {
            onNodeClick(clickedNode)
            return
        }

        // 再检测是否点击到边
        const clickedEdge = detectEdgeAt(canvasX, canvasY)
        if (clickedEdge) {
            onEdgeClick(clickedEdge)
            return
        }

        var pos = pixelToPhysical(mapInfo.value, canvasX, canvasY)
        console.log(`导航位置:${pos[0]},${pos[1]}`)
    }

    const handleDynamicContextMenu = (event) => {
        const canvas = event.target
        const rect = canvas.getBoundingClientRect()
        const scaleX = canvas.width / rect.width
        const scaleY = canvas.height / rect.height
        const canvasX = (event.clientX - rect.left) * scaleX
        const canvasY = (event.clientY - rect.top) * scaleY

        if (detectAgvAt(canvasX, canvasY)) {
            // console.log(`AGV右键: ${agvResponse.value.agvStatus.agvId}`)
            event.preventDefault()
            return
        }
        // 转发静态层右键处理（需提前提取静态层的右键逻辑）
        // 没有点击到AGV，将事件转发给静态canvas
        const staticCanvas = document.getElementById('mapCanvasStatic')
        if (staticCanvas) {
            const newEvent = new MouseEvent('contextmenu', {
                clientX: event.clientX,
                clientY: event.clientY,
                button: event.button,
                buttons: event.buttons,
                ctrlKey: event.ctrlKey,
                shiftKey: event.shiftKey,
                altKey: event.altKey,
                metaKey: event.metaKey
            })
            staticCanvas.dispatchEvent(newEvent)
        }
    }


    // AGV 命中检测（复用已有的坐标计算逻辑）
    const detectAgvAt = (canvasX, canvasY) => {
        if (!agvStatusList.value || agvStatusList.value.length === 0) return false
        // 计算 AGV 屏幕坐标（与 drawAgvPosition 保持一致）
        let screenX, screenY
        agvStatusList.value.forEach(agv => {
            // 计算 AGV 屏幕坐标（与 drawAgvPosition 保持一致）
            agvStatusList.value.forEach(agv => {
                if (agv.currentPosition?.x !== undefined) {
                    const pos = convertRobotPoseToWeb(mapInfo.value, agv.currentPosition.x, agv.currentPosition.y, agv.currentPosition?.theta || 0)
                    screenX = pos.pixelX * zoomState.level
                    screenY = pos.pixelY * zoomState.level
                } else if (agv.currentNodeId) {
                    const node = nodes.value.find(n => n.id === agv.currentNodeId)
                    if (node) {
                        const pos = convertRobotPoseToWeb(mapInfo.value, node.x, node.y, node.theta)
                        screenX = pos.pixelX * zoomState.level
                        screenY = pos.pixelY * zoomState.level
                    } else return false
                } else return false

                // 点击阈值（AGV 图标半宽约 24px，可适当放大）
                const hitRadius = 30
                return Math.hypot(canvasX - screenX, canvasY - screenY) <= hitRadius
            })
        })
    }

    const detectAgvAtFromEvent = (event) => {
        const canvas = event.target
        const rect = canvas.getBoundingClientRect()
        const scaleX = canvas.width / rect.width
        const scaleY = canvas.height / rect.height
        const canvasX = (event.clientX - rect.left) * scaleX
        const canvasY = (event.clientY - rect.top) * scaleY
        return detectAgvAt(canvasX, canvasY)
    }


    const detectNodeAt = (canvasX, canvasY) => {
        // 从后向前遍历，使上层节点优先（后绘制的在上）
        for (let i = nodes.value.length - 1; i >= 0; i--) {
            const node = nodes.value[i]
            const nodePos = convertRobotPoseToWeb(mapInfo.value, node.x, node.y, node.theta)
            const screenX = nodePos.pixelX * zoomState.level
            const screenY = nodePos.pixelY * zoomState.level
            let radius = 6
            if (node.type === 'STATION') radius = 9
            else if (node.type === 'INTERSECTION') radius = 7
            // 考虑绘制的额外半径（当节点在规划路线上时，会绘制更大的圆）
            const hitRadius = radius + 2 // 增加点击区域
            const distance = Math.hypot(canvasX - screenX, canvasY - screenY)
            if (distance <= hitRadius) {
                return node
            }
        }
        return null
    }

    const detectEdgeAt = (canvasX, canvasY) => {
        const threshold = 5 // 像素阈值
        for (const edge of edges.value) {
            const source = nodes.value.find(n => n.id === edge.sourceId)
            const target = nodes.value.find(n => n.id === edge.targetId)
            if (!source || !target) continue
            const sourcePos = physicalToPixel(mapInfo.value, source.x, source.y)
            const targetPos = physicalToPixel(mapInfo.value, target.x, target.y)
            const x1 = sourcePos[0] * zoomState.level
            const y1 = sourcePos[1] * zoomState.level
            const x2 = targetPos[0] * zoomState.level
            const y2 = targetPos[1] * zoomState.level

            const distance = distancePointToSegment(canvasX, canvasY, x1, y1, x2, y2)
            if (distance <= threshold) {
                return edge
            }
        }
        return null
    }

    // 点到线段距离
    const distancePointToSegment = (x, y, x1, y1, x2, y2) => {
        const A = x - x1
        const B = y - y1
        const C = x2 - x1
        const D = y2 - y1
        const dot = A * C + B * D
        const len_sq = C * C + D * D
        let param = -1
        if (len_sq !== 0) param = dot / len_sq
        let xx, yy
        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * C
            yy = y1 + param * D
        }
        const dx = x - xx
        const dy = y - yy
        return Math.hypot(dx, dy)
    }

    const onNodeClick = (node) => {
        if (node.type === 'STATION') {
            // 显示站点位置信息
            const message = `站点ID: ${node.id}\n位置: x=${node.x.toFixed(2)}, y=${node.y.toFixed(2)}\n角度: ${node.theta?.toFixed(2) || 0} rad (${((node.theta || 0) * 180 / Math.PI).toFixed(1)}°)`
            console.log(message)
            ToastManager.showToast(message, 0) // 或者使用更友好的提示
            // 可以将信息存储到响应式变量中供模板显示
            selectedNodeInfo.value = {
                id: node.id,
                name: node.name,
                x: node.x,
                y: node.y,
                theta: node.theta
            }
            showNodeInfo.value = true
        } else {
            // 其他类型节点点击时可选处理，目前只要求站点
            console.log('点击了节点:', node.id, node.type)
        }
    }

    const onEdgeClick = (edge) => {
        console.log('点击了通道:', edge.id, '从', edge.sourceId, '到', edge.targetId)
    }

    const handleContextMenu = (event) => {
        event.preventDefault()
        canvasState.isMouseDown = false

        // 右键菜单处理：检测点击的元素并执行相应操作
        const canvas = event.target
        const rect = canvas.getBoundingClientRect()
        const scaleX = canvas.width / rect.width
        const scaleY = canvas.height / rect.height
        const canvasX = (event.clientX - rect.left) * scaleX
        const canvasY = (event.clientY - rect.top) * scaleY

        const clickedNode = detectNodeAt(canvasX, canvasY)
        if (clickedNode) {
            selectedNodeInfo.value = clickedNode

            // 右键站点：显示信息或菜单
            if (clickedNode.type === 'STATION') {
                // 可以显示一个简单的菜单，这里简化处理：同样显示信息，但使用不同提示
                console.log('右键点击STATION:', clickedNode.id)
                showContextMenu(event, clickedNode, 'node')
            } else {
                console.log('右键点击节点:', clickedNode.id)
            }
            return
        }

        const clickedEdge = detectEdgeAt(canvasX, canvasY)
        if (clickedEdge) {
            showContextMenu(event, clickedEdge, 'edge')
            console.log('右键点击通道:', clickedEdge.id)
        }
    }

    // 新增：显示右键菜单函数
    const showContextMenu = (event, targetData, targetType) => {
        // 计算菜单位置，确保不会超出屏幕边界
        const menuWidth = 180
        const menuHeight = targetType === 'node' ? 100 : 60

        let x = event.clientX
        let y = event.clientY

        // 检查右边界
        if (x + menuWidth > window.innerWidth) {
            x = window.innerWidth - menuWidth - 10
        }

        // 检查下边界
        if (y + menuHeight > window.innerHeight) {
            y = window.innerHeight - menuHeight - 10
        }

        contextMenu.x = x
        contextMenu.y = y
        contextMenu.targetData = targetData
        contextMenu.targetType = targetType
        contextMenu.visible = true
    }


// 新增：隐藏右键菜单函数
    const hideContextMenu = () => {
        contextMenu.visible = false
        contextMenu.targetData = null
        contextMenu.targetType = null
    }

    const handleCloseMenu = () => {
        hideContextMenu()
    }

    // 返回所有需要暴露的属性和方法
    return {
        // 响应式数据
        formData,
        mapInfoDisplay,
        planningResultDisplay,
        stationNodes,
        nodes,
        agvList,
        zoomState,
        // 方法
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
        handleDynamicContextMenu,
        handleDynamicWheel,
        handleDynamicMouseDown,
        handleDynamicMouseMove,
        handleDynamicMouseUp,
        handleContextMenu,
        initialize,
        // 新增API处理辅助方法
        handleApiResponse,
        // 计算属性
        filteredStationNodes: computed(() => filteredStationNodes.value),
        contextMenu
    }
}