// 读取PGM文件
const readPGMFile = (file) => {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (event) => {
            const arrayBuffer = event.target.result;
            const dataView = new DataView(arrayBuffer);
            resolve(dataView);
        };
        reader.onerror = reject;
        reader.readAsArrayBuffer(file);
    });
}

// 修改pgm_helper.js中的convertPGMtoCanvas函数
function convertPGMtoCanvas(canvas, dataView, zoom = 1.0) {
    const header = {};
    const fileSignature = String.fromCharCode(dataView.getUint8(0), dataView.getUint8(1));
    if (fileSignature !== "P5") {
        return null;
    }

    let offset = 2;
    let line = "";
    let have = false;

    // 解析注释
    line = "";
    while (line !== "\n" && !header.msg) {
        if (offset < 100) {
            const char = String.fromCharCode(dataView.getUint8(offset));
            if (char == "#") {
                have = true;
            }
            if (have && char !== "\n") {
                line += char;
            }
            if (char == "\n") {
                if (!header.msg) {
                    header.msg = line;
                    have = false;
                }
                line = "";
            }
            offset++;
        } else {
            offset = 2;
            break;
        }
    }

    // 解析宽度和高度
    while (line !== "\n" && (!header.width || !header.height)) {
        const char = String.fromCharCode(dataView.getUint8(offset));
        if (char !== " " && char !== "\n") {
            line += char;
        } else {
            if (!header.width) {
                header.width = parseInt(line);
            } else if (!header.height) {
                header.height = parseInt(line);
            }
            line = "";
        }
        offset++;
    }

    // 解析最大灰度值
    line = "";
    while (line !== "\n" && !header.maxGrayValue) {
        const char = String.fromCharCode(dataView.getUint8(offset));
        if (char !== " " && char !== "\n") {
            line += char;
        } else {
            if (!header.maxGrayValue) {
                header.maxGrayValue = parseInt(line);
            }
            line = "";
        }
        offset++;
    }

    const width = header.width;
    const height = header.height;
    console.log(`PGM地图文件大小: ${width}x${height}, 缩放: ${zoom}`);

    const context = canvas.getContext('2d');

    // 创建临时canvas用于缩放
    const tempCanvas = document.createElement('canvas');
    tempCanvas.width = width;
    tempCanvas.height = height;
    const tempContext = tempCanvas.getContext('2d');

    const imageData = tempContext.createImageData(width, height);
    const data = imageData.data;

    // 读取像素值到临时canvas
    for (let i = 0; i < width * height; i++) {
        const pixelValue = dataView.getUint8(offset + i);
        const index = i * 4;

        data[index] = pixelValue;     // R
        data[index + 1] = pixelValue; // G
        data[index + 2] = pixelValue; // B
        data[index + 3] = 255;        // A
    }

    tempContext.putImageData(imageData, 0, 0);

    // 在原始canvas上绘制缩放后的图像
    const scaledWidth = width * zoom;
    const scaledHeight = height * zoom;

    // 清空原始canvas
    context.clearRect(0, 0, canvas.width, canvas.height);

    // 绘制缩放后的图像
    context.drawImage(tempCanvas, 0, 0, width, height, 0, 0, scaledWidth, scaledHeight);

    return {
        width: width,
        height: height,
        scaledWidth: scaledWidth,
        scaledHeight: scaledHeight,
        zoom: zoom
    };
}

// 将Canvas保存为PNG文件
const saveCanvasAsPNG = (canvas) => {
    return new Promise((resolve, reject) => {
        canvas.toBlob((blob) => {
            let imgLink = URL.createObjectURL(blob)
            resolve(imgLink);
        }, 'image/png');
    });
}

/*
export const pgmToPng = (file) => {
    return new Promise((resolve, reject) => {
        readPGMFile(file)
            .then((dataView) => convertPGMtoCanvas(dataView))
            .then((canvas) => saveCanvasAsPNG(canvas))
            .then((imgLink) => {
                resolve(imgLink)
            })
            .catch((error) => console.error('转换失败', error));
    });

}*/
