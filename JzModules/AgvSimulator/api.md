# 1. 暂停AGV
curl -X POST "http://localhost:22777/api/simulator/control" \
-H "Content-Type: application/json" \
-d '{
"command": "PAUSE",
"parameters": {}
}'

# 2. 恢复AGV运行
curl -X POST "http://localhost:22777/api/simulator/control" \
-H "Content-Type: application/json" \
-d '{
"command": "RESUME",
"parameters": {}
}'

# 3. 紧急停止
curl -X POST "http://localhost:22777/api/simulator/control" \
-H "Content-Type: application/json" \
-d '{
"command": "EMERGENCY_STOP",
"parameters": {}
}'

# 4. 重置急停状态
curl -X POST "http://localhost:22777/api/simulator/control" \
-H "Content-Type: application/json" \
-d '{
"command": "RESET_EMERGENCY",
"parameters": {}
}'

# 5. 取消当前任务
curl -X POST "http://localhost:22777/api/simulator/control" \
-H "Content-Type: application/json" \
-d '{
"command": "CANCEL",
"parameters": {}
}'

# 6. 修改AGV速度
curl -X POST "http://localhost:22777/api/simulator/control" \
-H "Content-Type: application/json" \
-d '{
"command": "CHANGE_SPEED",
"parameters": {
"speed": 1.5
}
}'

# 7. 修改AGV电量
curl -X POST "http://localhost:22777/api/simulator/control" \
-H "Content-Type: application/json" \
-d '{
"command": "CHANGE_BATTERY",
"parameters": {
"level": 80.0
}
}'