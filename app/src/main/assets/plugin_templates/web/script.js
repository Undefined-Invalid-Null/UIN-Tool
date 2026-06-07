// ==================== Web 插件测试面板 JavaScript ====================
// 版本：1.0.0
// 说明：完整的 Web 插件功能测试面板，支持所有 UINPlugin API

// 全局变量
let currentSensorCallback = null;
let sensorRunning = false;
let currentSensorType = null;
let counter = 0;

// ==================== 初始化 ====================
document.addEventListener('DOMContentLoaded', function() {
    addLog('info', 'Web 插件测试面板已启动');
    
    // 检查 UINPlugin 是否可用
    if (typeof UINPlugin !== 'undefined') {
        addLog('success', 'UINPlugin 接口: ✅ 可用');
        
        // 获取插件信息
        try {
            const pluginInfo = JSON.parse(UINPlugin.getPluginInfo());
            addLog('info', '插件名称: ' + (pluginInfo.name || '未知'));
            addLog('info', '插件版本: ' + (pluginInfo.versionName || '未知'));
        } catch(e) {
            addLog('warning', '获取插件信息失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 接口: ❌ 不可用');
    }
    
    // 初始化标签页切换
    initTabs();
    
    // 自动加载基础信息
    setTimeout(() => {
        testGetDeviceInfo();
        testGetNetworkInfo();
        testGetTime();
        testGetAppVersion();
    }, 500);
});

// ==================== 标签页切换 ====================
function initTabs() {
    const tabBtns = document.querySelectorAll('.tab-btn');
    tabBtns.forEach(btn => {
        btn.addEventListener('click', function() {
            const tabId = this.getAttribute('data-tab');
            
            // 切换按钮样式
            tabBtns.forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            
            // 切换内容
            document.querySelectorAll('.tab-pane').forEach(pane => {
                pane.classList.remove('active');
            });
            const targetPane = document.getElementById('tab-' + tabId);
            if (targetPane) {
                targetPane.classList.add('active');
            }
            
            addLog('info', '切换到标签页: ' + tabId);
        });
    });
}

// ==================== 日志系统 ====================
function addLog(type, message) {
    const logContainer = document.getElementById('logContainer');
    if (!logContainer) return;
    
    const timestamp = new Date().toLocaleTimeString();
    const logEntry = document.createElement('div');
    logEntry.className = 'log-entry ' + type;
    
    let icon = '';
    switch(type) {
        case 'success': icon = '✅'; break;
        case 'error': icon = '❌'; break;
        case 'warning': icon = '⚠️'; break;
        case 'info': icon = 'ℹ️'; break;
        default: icon = '📝';
    }
    
    logEntry.innerHTML = `<span class="log-time">[${timestamp}]</span> ${icon} ${escapeHtml(message)}`;
    logContainer.appendChild(logEntry);
    logContainer.scrollTop = logContainer.scrollHeight;
    
    // 限制日志数量
    while (logContainer.children.length > 100) {
        logContainer.removeChild(logContainer.firstChild);
    }
    
    // 同时输出到控制台
    console.log(`[${type}] ${message}`);
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>]/g, function(m) {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        return m;
    });
}

function clearLog() {
    const logContainer = document.getElementById('logContainer');
    if (logContainer) {
        logContainer.innerHTML = '<div class="log-entry system">📋 日志已清空</div>';
        addLog('info', '日志已清空');
    }
}

function testConsoleLog() {
    console.log('测试控制台输出');
    console.info('信息输出');
    console.warn('警告输出');
    console.error('错误输出');
    addLog('info', '已输出测试日志到控制台，请查看开发者工具');
}

// ==================== 辅助函数 ====================
function updateResult(elementId, content, isError = false) {
    const element = document.getElementById(elementId);
    if (element) {
        element.textContent = content;
        element.style.color = isError ? '#dc3545' : '';
    }
}

function formatJSON(data) {
    try {
        if (typeof data === 'string') {
            const parsed = JSON.parse(data);
            return JSON.stringify(parsed, null, 2);
        }
        return JSON.stringify(data, null, 2);
    } catch(e) {
        return data;
    }
}

// ==================== 基础功能测试 ====================
function testToast() {
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('toast', '这是一条短提示消息');
        addLog('success', '已发送短提示');
    } else {
        addLog('error', 'UINPlugin 未定义');
        alert('UINPlugin 未定义');
    }
}

function testLongToast() {
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('toast', '这是一条较长的提示消息，用于测试长文本显示效果');
        addLog('success', '已发送长提示');
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testAlert() {
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('alert', '这是一个弹窗提示');
        addLog('info', '已触发弹窗');
    } else {
        alert('这是一个备用弹窗');
    }
}

function testConfirm() {
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('confirm', '确定要继续吗？');
        addLog('info', '已触发确认框');
    } else {
        if (confirm('确定要继续吗？')) {
            addLog('info', '用户点击了确定');
        } else {
            addLog('info', '用户点击了取消');
        }
    }
}

function testCopy() {
    const text = document.getElementById('copyText').value;
    if (!text) {
        addLog('warning', '请输入要复制的文字');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('copy', text);
        addLog('success', '已复制: ' + text);
        alert('已复制: ' + text);
    } else {
        try {
            navigator.clipboard.writeText(text);
            addLog('success', '已复制: ' + text);
            alert('已复制: ' + text);
        } catch(e) {
            addLog('error', '复制失败: ' + e.message);
        }
    }
}

function testPaste() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const text = UINPlugin.paste();
            document.getElementById('pasteResult').value = text;
            addLog('success', '已粘贴: ' + (text.length > 50 ? text.substring(0, 50) + '...' : text));
        } catch(e) {
            addLog('error', '粘贴失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testVibrate() {
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('vibrate', '100');
        addLog('info', '震动 100ms');
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testVibrateLong() {
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('vibrate', '500');
        addLog('info', '震动 500ms');
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testShare() {
    const text = '来自 UIN Tool Web 插件的分享测试\n时间: ' + new Date().toLocaleString();
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('share', text);
        addLog('info', '已触发分享');
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testOpenUrl() {
    const url = document.getElementById('urlInput').value;
    if (!url) {
        addLog('warning', '请输入 URL');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.callHost('openUrl', url);
        addLog('info', '打开链接: ' + url);
    } else {
        window.open(url, '_blank');
    }
}

// ==================== 信息获取测试 ====================
function testGetPluginInfo() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const info = JSON.parse(UINPlugin.getPluginInfo());
            const resultDiv = document.getElementById('pluginInfoResult');
            if (resultDiv) {
                resultDiv.textContent = formatJSON(info);
            }
            addLog('success', '插件信息: ' + (info.name || '未知') + ' v' + (info.versionName || '未知'));
        } catch(e) {
            addLog('error', '解析插件信息失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testGetDeviceInfo() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const info = JSON.parse(UINPlugin.getDeviceInfo());
            
            const brandEl = document.getElementById('device-brand');
            const modelEl = document.getElementById('device-model');
            const androidEl = document.getElementById('device-android');
            const apiEl = document.getElementById('device-api');
            const screenEl = document.getElementById('device-screen');
            const densityEl = document.getElementById('device-density');
            
            if (brandEl) brandEl.textContent = info.brand || '-';
            if (modelEl) modelEl.textContent = info.device || '-';
            if (androidEl) androidEl.textContent = info.android || '-';
            if (apiEl) apiEl.textContent = info.api || '-';
            if (screenEl) screenEl.textContent = (info.screenWidth || 0) + 'x' + (info.screenHeight || 0);
            if (densityEl) densityEl.textContent = (info.screenDensityDpi || 0) + ' dpi';
            
            addLog('success', '设备信息: ' + (info.brand || '') + ' ' + (info.device || ''));
        } catch(e) {
            addLog('error', '解析设备信息失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testGetNetworkInfo() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const info = JSON.parse(UINPlugin.getNetworkInfo());
            
            const statusEl = document.getElementById('network-status');
            const typeEl = document.getElementById('network-type');
            const wifiEl = document.getElementById('network-wifi');
            const mobileEl = document.getElementById('network-mobile');
            
            if (statusEl) statusEl.textContent = info.connected ? '🟢 已连接' : '🔴 未连接';
            if (typeEl) typeEl.textContent = info.type || '-';
            if (wifiEl) wifiEl.textContent = info.isWifi ? '是' : '否';
            if (mobileEl) mobileEl.textContent = info.isMobile ? '是' : '否';
            
            addLog('info', '网络状态: ' + (info.connected ? '已连接' : '未连接'));
        } catch(e) {
            addLog('error', '解析网络信息失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testGetTime() {
    if (typeof UINPlugin !== 'undefined') {
        const time = UINPlugin.getCurrentTime();
        const timeEl = document.getElementById('current-time');
        if (timeEl) timeEl.textContent = time;
        addLog('info', '当前时间: ' + time);
    } else {
        const timeEl = document.getElementById('current-time');
        if (timeEl) timeEl.textContent = new Date().toLocaleString();
    }
}

function testGetAppVersion() {
    if (typeof UINPlugin !== 'undefined') {
        const version = UINPlugin.getAppVersion();
        const versionEl = document.getElementById('app-version');
        if (versionEl) versionEl.textContent = version;
        addLog('info', '宿主版本: ' + version);
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

// ==================== 存储功能测试 ====================
function testSetStorage() {
    const key = document.getElementById('storageKey').value;
    const value = document.getElementById('storageValue').value;
    
    if (!key) {
        addLog('warning', '请输入键名');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.setStorage(key, value || '');
        addLog('success', `已保存: ${key} = ${value || '(空)'}`);
        alert(`已保存: ${key} = ${value || '(空)'}`);
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testGetStorage() {
    const key = document.getElementById('storageGetKey').value;
    
    if (!key) {
        addLog('warning', '请输入键名');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        const value = UINPlugin.getStorage(key);
        const resultEl = document.getElementById('storageResult');
        if (resultEl) resultEl.textContent = value || '(空)';
        addLog('info', `读取: ${key} = ${value || '(空)'}`);
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testRemoveStorage() {
    const key = document.getElementById('storageGetKey').value;
    
    if (!key) {
        addLog('warning', '请输入键名');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.removeStorage(key);
        const resultEl = document.getElementById('storageResult');
        if (resultEl) resultEl.textContent = '(已删除)';
        addLog('success', `已删除: ${key}`);
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testClearStorage() {
    if (typeof UINPlugin !== 'undefined') {
        UINPlugin.clearStorage();
        const resultEl = document.getElementById('storageResult');
        if (resultEl) resultEl.textContent = '(已清空)';
        addLog('success', '已清空所有存储数据');
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

// ==================== 文件系统测试 ====================
function testWriteFile() {
    const fileName = document.getElementById('fileName').value;
    const content = document.getElementById('fileContent').value;
    
    if (!fileName) {
        addLog('warning', '请输入文件名');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        try {
            const success = UINPlugin.writeFile(fileName, content || '');
            if (success) {
                addLog('success', `文件已写入: ${fileName}`);
                const resultEl = document.getElementById('fileResult');
                if (resultEl) resultEl.textContent = `✅ 文件已写入: ${fileName}\n内容长度: ${(content || '').length} 字符`;
                testListFiles();
            } else {
                addLog('error', `写入失败: ${fileName}`);
                const resultEl = document.getElementById('fileResult');
                if (resultEl) resultEl.textContent = `❌ 写入失败: ${fileName}`;
            }
        } catch(e) {
            addLog('error', `写入异常: ${e.message}`);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testReadFile() {
    const fileName = document.getElementById('fileName').value;
    
    if (!fileName) {
        addLog('warning', '请输入文件名');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        try {
            const content = UINPlugin.readFile(fileName);
            if (content !== null && content !== undefined) {
                const contentEl = document.getElementById('fileContent');
                const resultEl = document.getElementById('fileResult');
                if (contentEl) contentEl.value = content;
                if (resultEl) resultEl.textContent = `📄 文件内容 (${fileName}):\n${content}`;
                addLog('success', `已读取: ${fileName}`);
            } else {
                const resultEl = document.getElementById('fileResult');
                if (resultEl) resultEl.textContent = `❌ 文件不存在: ${fileName}`;
                addLog('warning', `文件不存在: ${fileName}`);
            }
        } catch(e) {
            addLog('error', `读取异常: ${e.message}`);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testDeleteFile() {
    const fileName = document.getElementById('fileName').value;
    
    if (!fileName) {
        addLog('warning', '请输入文件名');
        return;
    }
    
    if (typeof UINPlugin !== 'undefined') {
        try {
            const success = UINPlugin.deleteFile(fileName);
            if (success) {
                addLog('success', `文件已删除: ${fileName}`);
                const resultEl = document.getElementById('fileResult');
                if (resultEl) resultEl.textContent = `🗑️ 文件已删除: ${fileName}`;
                testListFiles();
            } else {
                addLog('warning', `删除失败: ${fileName}`);
                const resultEl = document.getElementById('fileResult');
                if (resultEl) resultEl.textContent = `❌ 删除失败: ${fileName}`;
            }
        } catch(e) {
            addLog('error', `删除异常: ${e.message}`);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testListFiles() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const files = UINPlugin.listFiles('');
            const select = document.getElementById('fileList');
            if (select) {
                select.innerHTML = '';
                
                if (files && files.length > 0) {
                    files.forEach(file => {
                        const option = document.createElement('option');
                        option.value = file;
                        option.textContent = file;
                        select.appendChild(option);
                    });
                    const resultEl = document.getElementById('fileResult');
                    if (resultEl) resultEl.textContent = `📁 找到 ${files.length} 个文件:\n${files.join('\n')}`;
                    addLog('info', `找到 ${files.length} 个文件`);
                } else {
                    select.innerHTML = '<option>-- 无文件 --</option>';
                    const resultEl = document.getElementById('fileResult');
                    if (resultEl) resultEl.textContent = '📁 插件目录为空';
                    addLog('info', '插件目录为空');
                }
            }
        } catch(e) {
            addLog('error', `列出文件失败: ${e.message}`);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function loadSelectedFile() {
    const select = document.getElementById('fileList');
    if (select && select.value && select.value !== '-- 无文件 --') {
        document.getElementById('fileName').value = select.value;
        testReadFile();
    }
}

function testGetPluginDir() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const dir = UINPlugin.getPluginDir();
            const resultEl = document.getElementById('pluginDirResult');
            if (resultEl) resultEl.textContent = dir;
            addLog('info', '插件目录: ' + dir);
        } catch(e) {
            addLog('error', '获取目录失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

// ==================== 网络请求测试 ====================
function testHttpGet() {
    const url = document.getElementById('getUrl').value;
    if (!url) {
        addLog('warning', '请输入 URL');
        return;
    }
    
    addLog('info', `发送 GET 请求: ${url}`);
    const resultEl = document.getElementById('getResult');
    if (resultEl) resultEl.textContent = '加载中...';
    
    const callbackId = 'get_' + Date.now();
    
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(response) {
        try {
            let data;
            if (typeof response === 'string') {
                data = JSON.parse(response);
            } else {
                data = response;
            }
            
            if (data.success) {
                try {
                    let parsedData = data.data;
                    if (typeof parsedData === 'string') {
                        parsedData = JSON.parse(parsedData);
                    }
                    if (resultEl) resultEl.textContent = formatJSON(parsedData);
                    addLog('success', `GET 请求成功，状态码: ${data.statusCode}`);
                } catch(e) {
                    if (resultEl) resultEl.textContent = data.data;
                    addLog('success', `GET 请求成功`);
                }
            } else {
                if (resultEl) resultEl.textContent = `错误: ${data.error}`;
                addLog('error', `GET 请求失败: ${data.error}`);
            }
        } catch(e) {
            if (resultEl) resultEl.textContent = `解析错误: ${e.message}`;
            addLog('error', `解析响应失败: ${e.message}`);
        }
        delete window.UINPluginCallbacks[callbackId];
    };
    
    try {
        UINPlugin.httpGet(url, callbackId);
    } catch(e) {
        addLog('error', '调用 httpGet 失败: ' + e.message);
    }
}

function testHttpPost() {
    const url = document.getElementById('postUrl').value;
    const data = document.getElementById('postData').value;
    
    if (!url) {
        addLog('warning', '请输入 URL');
        return;
    }
    
    addLog('info', `发送 POST 请求: ${url}`);
    const resultEl = document.getElementById('postResult');
    if (resultEl) resultEl.textContent = '加载中...';
    
    const callbackId = 'post_' + Date.now();
    
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(response) {
        try {
            let respData;
            if (typeof response === 'string') {
                respData = JSON.parse(response);
            } else {
                respData = response;
            }
            
            if (respData.success) {
                try {
                    let parsedData = respData.data;
                    if (typeof parsedData === 'string') {
                        parsedData = JSON.parse(parsedData);
                    }
                    if (resultEl) resultEl.textContent = formatJSON(parsedData);
                    addLog('success', `POST 请求成功，状态码: ${respData.statusCode}`);
                } catch(e) {
                    if (resultEl) resultEl.textContent = respData.data;
                    addLog('success', `POST 请求成功`);
                }
            } else {
                if (resultEl) resultEl.textContent = `错误: ${respData.error}`;
                addLog('error', `POST 请求失败: ${respData.error}`);
            }
        } catch(e) {
            if (resultEl) resultEl.textContent = `解析错误: ${e.message}`;
            addLog('error', `解析响应失败: ${e.message}`);
        }
        delete window.UINPluginCallbacks[callbackId];
    };
    
    try {
        UINPlugin.httpPost(url, data || '{}', callbackId);
    } catch(e) {
        addLog('error', '调用 httpPost 失败: ' + e.message);
    }
}

function testGitHubAPI() {
    const urlInput = document.getElementById('getUrl');
    if (urlInput) urlInput.value = 'https://api.github.com/orgs/UIN-Tool-Plugins/repos';
    testHttpGet();
}

function testHttpBin() {
    const urlInput = document.getElementById('postUrl');
    const dataArea = document.getElementById('postData');
    if (urlInput) urlInput.value = 'https://httpbin.org/post';
    if (dataArea) {
        dataArea.value = JSON.stringify({
            test: 'Hello World',
            timestamp: new Date().toISOString(),
            source: 'UIN Tool Web Plugin'
        }, null, 2);
    }
    addLog('info', '已准备 HttpBin POST 测试数据');
}

function testIPAPI() {
    const urlInput = document.getElementById('getUrl');
    if (urlInput) urlInput.value = 'https://api.ipify.org?format=json';
    testHttpGet();
}

// ==================== 传感器测试 ====================
function testGetAvailableSensors() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const sensors = JSON.parse(UINPlugin.getAvailableSensors());
            const available = [];
            for (const [name, availableFlag] of Object.entries(sensors)) {
                if (availableFlag) available.push(name);
            }
            const sensorsEl = document.getElementById('available-sensors');
            if (sensorsEl) sensorsEl.textContent = available.length > 0 ? available.join(', ') : '无可用传感器';
            addLog('info', `可用传感器: ${available.join(', ') || '无'}`);
        } catch(e) {
            addLog('error', '获取传感器列表失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function startSensor(type) {
    if (sensorRunning) {
        stopAllSensors();
    }
    
    addLog('info', `启动传感器: ${type}`);
    currentSensorType = type;
    const currentSensorEl = document.getElementById('current-sensor');
    if (currentSensorEl) currentSensorEl.textContent = type;
    
    const sensorDataDiv = document.getElementById('sensorData');
    if (sensorDataDiv) sensorDataDiv.innerHTML = '<div class="sensor-loading">等待传感器数据...</div>';
    
    const callbackId = 'sensor_' + type + '_' + Date.now();
    
    window.UINPluginCallbacks = window.UINPluginCallbacks || {};
    window.UINPluginCallbacks[callbackId] = function(data) {
        try {
            let sensorData;
            if (typeof data === 'string') {
                sensorData = JSON.parse(data);
            } else {
                sensorData = data;
            }
            
            if (sensorData.success) {
                let html = '<div class="sensor-data">';
                html += `<div class="sensor-title">📊 ${getSensorName(type)}</div>`;
                html += `<div class="sensor-values">`;
                
                switch(type) {
                    case 'accelerometer':
                        html += `<div>X: <span class="sensor-value">${(sensorData.x || 0).toFixed(3)}</span> m/s²</div>`;
                        html += `<div>Y: <span class="sensor-value">${(sensorData.y || 0).toFixed(3)}</span> m/s²</div>`;
                        html += `<div>Z: <span class="sensor-value">${(sensorData.z || 0).toFixed(3)}</span> m/s²</div>`;
                        break;
                    case 'gyroscope':
                        html += `<div>X: <span class="sensor-value">${(sensorData.x || 0).toFixed(3)}</span> rad/s</div>`;
                        html += `<div>Y: <span class="sensor-value">${(sensorData.y || 0).toFixed(3)}</span> rad/s</div>`;
                        html += `<div>Z: <span class="sensor-value">${(sensorData.z || 0).toFixed(3)}</span> rad/s</div>`;
                        break;
                    case 'magnetic':
                        html += `<div>X: <span class="sensor-value">${(sensorData.x || 0).toFixed(2)}</span> μT</div>`;
                        html += `<div>Y: <span class="sensor-value">${(sensorData.y || 0).toFixed(2)}</span> μT</div>`;
                        html += `<div>Z: <span class="sensor-value">${(sensorData.z || 0).toFixed(2)}</span> μT</div>`;
                        break;
                    case 'light':
                        html += `<div>光照强度: <span class="sensor-value">${(sensorData.lux || 0).toFixed(2)}</span> lux</div>`;
                        break;
                    case 'proximity':
                        html += `<div>距离: <span class="sensor-value">${(sensorData.distance || 0).toFixed(2)}</span> cm</div>`;
                        break;
                    case 'pressure':
                        html += `<div>气压: <span class="sensor-value">${(sensorData.pressure || 0).toFixed(2)}</span> hPa</div>`;
                        break;
                    default:
                        html += `<div>数据: <span class="sensor-value">${JSON.stringify(sensorData.values)}</span></div>`;
                }
                
                html += `<div class="sensor-timestamp">时间戳: ${sensorData.timestamp}</div>`;
                if (sensorData.accuracy !== undefined) {
                    html += `<div>精度: ${sensorData.accuracy}</div>`;
                }
                html += `</div></div>`;
                
                const sensorDiv = document.getElementById('sensorData');
                if (sensorDiv) sensorDiv.innerHTML = html;
            } else if (sensorData.type === 'accuracy') {
                addLog('info', `传感器精度变化: ${sensorData.accuracy}`);
            } else {
                const sensorDiv = document.getElementById('sensorData');
                if (sensorDiv) sensorDiv.innerHTML = `<div class="sensor-error">传感器错误: ${sensorData.error}</div>`;
                addLog('error', `传感器错误: ${sensorData.error}`);
            }
        } catch(e) {
            console.error('解析传感器数据失败:', e);
        }
    };
    
    try {
        UINPlugin.startSensor(type, callbackId);
        sensorRunning = true;
        currentSensorCallback = callbackId;
    } catch(e) {
        const sensorDiv = document.getElementById('sensorData');
        if (sensorDiv) sensorDiv.innerHTML = `<div class="sensor-error">启动失败: ${e.message}</div>`;
        addLog('error', `启动传感器失败: ${e.message}`);
    }
}

function stopAllSensors() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            UINPlugin.stopSensor();
            sensorRunning = false;
            currentSensorType = null;
            currentSensorCallback = null;
            const currentSensorEl = document.getElementById('current-sensor');
            if (currentSensorEl) currentSensorEl.textContent = '未启动';
            const sensorDiv = document.getElementById('sensorData');
            if (sensorDiv) sensorDiv.innerHTML = '<div class="sensor-placeholder">传感器已停止</div>';
            addLog('info', '已停止所有传感器');
        } catch(e) {
            addLog('error', '停止传感器失败: ' + e.message);
        }
    }
}

function getSensorName(type) {
    const names = {
        'accelerometer': '加速度计',
        'gyroscope': '陀螺仪',
        'magnetic': '磁场计',
        'light': '光线传感器',
        'proximity': '接近传感器',
        'pressure': '压力传感器'
    };
    return names[type] || type;
}

// ==================== 系统功能测试 ====================
function testOpenSettings() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            UINPlugin.openSettings();
            addLog('info', '打开系统设置');
        } catch(e) {
            addLog('error', '打开设置失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testOpenAppSettings() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            UINPlugin.openAppSettings();
            addLog('info', '打开应用设置');
        } catch(e) {
            addLog('error', '打开应用设置失败: ' + e.message);
        }
    } else {
        addLog('error', 'UINPlugin 未定义');
    }
}

function testSetTitle() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            const newTitle = '🎉 ' + new Date().toLocaleTimeString();
            UINPlugin.setTitle(newTitle);
            addLog('info', '已修改标题: ' + newTitle);
        } catch(e) {
            addLog('error', '修改标题失败: ' + e.message);
        }
    } else {
        document.title = '🎉 ' + new Date().toLocaleTimeString();
    }
}

function testResetTitle() {
    const pluginNameEl = document.getElementById('info-name');
    const pluginName = pluginNameEl ? pluginNameEl.textContent : 'Web 插件测试面板';
    
    if (typeof UINPlugin !== 'undefined') {
        try {
            UINPlugin.setTitle(pluginName);
            addLog('info', '已重置标题');
        } catch(e) {
            addLog('error', '重置标题失败: ' + e.message);
        }
    } else {
        document.title = pluginName;
    }
}

function testSetFullscreen() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            UINPlugin.setFullscreen(true);
            addLog('info', '已切换到全屏模式');
        } catch(e) {
            addLog('error', '全屏模式失败: ' + e.message);
        }
    } else if (document.documentElement.requestFullscreen) {
        document.documentElement.requestFullscreen();
    }
}

function testExitFullscreen() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            UINPlugin.setFullscreen(false);
            addLog('info', '已退出全屏模式');
        } catch(e) {
            addLog('error', '退出全屏失败: ' + e.message);
        }
    } else if (document.exitFullscreen) {
        document.exitFullscreen();
    }
}

function testClose() {
    if (typeof UINPlugin !== 'undefined') {
        try {
            UINPlugin.callHost('finish', '');
            addLog('info', '正在关闭插件...');
        } catch(e) {
            addLog('error', '关闭失败: ' + e.message);
        }
    } else {
        window.close();
    }
}

function incrementCounter() {
    counter++;
    const counterEl = document.getElementById('counter');
    if (counterEl) counterEl.textContent = counter;
    addLog('info', `计数器: ${counter}`);
}

function resetCounter() {
    counter = 0;
    const counterEl = document.getElementById('counter');
    if (counterEl) counterEl.textContent = counter;
    addLog('info', '计数器已重置');
}

// ==================== 生命周期事件 ====================
window.addEventListener('resume', function() {
    addLog('success', '插件已恢复');
    console.log('Web 插件已恢复');
});

window.addEventListener('pause', function() {
    addLog('info', '插件已暂停');
    console.log('Web 插件已暂停');
});

window.addEventListener('destroy', function() {
    console.log('Web 插件已销毁');
    if (sensorRunning) {
        stopAllSensors();
    }
});

// ==================== 错误处理 ====================
window.addEventListener('error', function(e) {
    addLog('error', `JavaScript 错误: ${e.message}`);
    console.error(e);
});

window.addEventListener('unhandledrejection', function(e) {
    addLog('error', `Promise 错误: ${e.reason}`);
    console.error(e);
});