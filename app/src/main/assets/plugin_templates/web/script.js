// ==================== 日志工具 ====================
function addLog(message, type = 'info') {
    const logContainer = document.getElementById('logContainer');
    if (!logContainer) return;
    
    const time = new Date().toLocaleTimeString();
    const logEntry = document.createElement('div');
    logEntry.className = `log-entry ${type}`;
    logEntry.textContent = `[${time}] ${message}`;
    logContainer.appendChild(logEntry);
    logContainer.scrollTop = logContainer.scrollHeight;
    
    while (logContainer.children.length > 100) {
        logContainer.removeChild(logContainer.firstChild);
    }
}

function clearLog() {
    const logContainer = document.getElementById('logContainer');
    if (logContainer) {
        logContainer.innerHTML = '<div class="log-entry system">📋 日志已清空</div>';
    }
    addLog('日志已清空', 'system');
}

// ==================== 基础功能测试 ====================
function testToast() {
    UINPlugin.callHost('toast', '这是短提示消息');
    addLog('调用 toast: 短提示', 'success');
}

function testLongToast() {
    UINPlugin.callHost('toast', '这是长提示消息，会显示更长时间');
    addLog('调用 toast: 长提示', 'success');
}

function testAlert() {
    UINPlugin.callHost('alert', '这是一个弹窗提示');
    addLog('调用 alert', 'info');
}

function testConfirm() {
    UINPlugin.callHost('confirm', '请确认操作');
    addLog('调用 confirm', 'info');
}

function testVibrate() {
    UINPlugin.callHost('vibrate', '100');
    addLog('调用 vibrate: 震动100ms', 'success');
}

function testVibrateLong() {
    UINPlugin.callHost('vibrate', '500');
    addLog('调用 vibrate: 震动500ms', 'success');
}

function testCopy() {
    const text = document.getElementById('copyText')?.value || '测试文本';
    UINPlugin.callHost('copy', text);
    addLog(`调用 copy: "${text}"`, 'success');
}

function testPaste() {
    try {
        if (typeof UINPlugin.paste === 'function') {
            const text = UINPlugin.paste();
            const pasteResult = document.getElementById('pasteResult');
            if (pasteResult) {
                if (text && text.length > 0) {
                    pasteResult.value = text;
                    addLog(`粘贴成功: ${text.substring(0, 50)}${text.length > 50 ? '...' : ''}`, 'success');
                } else {
                    pasteResult.value = '';
                    addLog('剪贴板为空', 'warning');
                }
            }
        } else {
            addLog('UINPlugin.paste 方法不存在', 'error');
        }
    } catch (e) {
        addLog(`粘贴失败: ${e.message}`, 'error');
    }
}

function testShare() {
    const text = '通过 UIN Tool 分享的测试内容';
    UINPlugin.callHost('share', text);
    addLog(`调用 share: "${text}"`, 'success');
}

function testOpenUrl() {
    const url = document.getElementById('urlInput')?.value || 'https://www.baidu.com';
    UINPlugin.callHost('openUrl', url);
    addLog(`调用 openUrl: ${url}`, 'success');
}

function testClose() {
    addLog('调用 finish: 关闭插件', 'warning');
    UINPlugin.callHost('finish', '');
}

// ==================== 信息获取测试 ====================
function testGetPluginInfo() {
    try {
        if (typeof UINPlugin.getPluginInfo === 'function') {
            const infoStr = UINPlugin.getPluginInfo();
            const info = JSON.parse(infoStr);
            addLog(`获取插件信息成功`, 'success');
            
            document.getElementById('info-name').innerText = info.name || '未知';
            document.getElementById('info-id').innerText = info.pluginId || '未知';
            document.getElementById('info-version').innerText = info.versionName || '未知';
            document.getElementById('info-author').innerText = info.author || '未知';
            
            const resultBox = document.getElementById('pluginDirResult');
            if (resultBox) {
                resultBox.innerHTML = `<strong>插件信息：</strong><br>${JSON.stringify(info, null, 2)}`;
            }
        } else {
            addLog('UINPlugin.getPluginInfo 不存在', 'error');
        }
    } catch (e) {
        addLog(`获取插件信息失败: ${e.message}`, 'error');
    }
}

function testGetDeviceInfo() {
    try {
        if (typeof UINPlugin.getDeviceInfo === 'function') {
            const infoStr = UINPlugin.getDeviceInfo();
            const info = JSON.parse(infoStr);
            addLog(`获取设备信息成功`, 'success');
            
            document.getElementById('device-brand').innerText = info.brand || '未知';
            document.getElementById('device-model').innerText = info.device || '未知';
            document.getElementById('device-android').innerText = info.android || '未知';
            document.getElementById('device-api').innerText = info.api || '未知';
        } else {
            addLog('UINPlugin.getDeviceInfo 不存在', 'error');
        }
    } catch (e) {
        addLog(`获取设备信息失败: ${e.message}`, 'error');
    }
}

function testGetNetworkInfo() {
    try {
        if (typeof UINPlugin.getNetworkInfo === 'function') {
            const infoStr = UINPlugin.getNetworkInfo();
            const info = JSON.parse(infoStr);
            addLog(`获取网络信息成功`, 'success');
            
            document.getElementById('network-status').innerText = info.connected ? '已连接' : '未连接';
            document.getElementById('network-type').innerText = info.type || '未知';
        } else {
            addLog('UINPlugin.getNetworkInfo 不存在', 'error');
        }
    } catch (e) {
        addLog(`获取网络信息失败: ${e.message}`, 'error');
    }
}

function testGetTime() {
    try {
        if (typeof UINPlugin.getCurrentTime === 'function') {
            const time = UINPlugin.getCurrentTime();
            document.getElementById('current-time').innerText = time;
            addLog(`获取当前时间: ${time}`, 'success');
        } else {
            addLog('UINPlugin.getCurrentTime 不存在', 'error');
        }
    } catch (e) {
        addLog(`获取时间失败: ${e.message}`, 'error');
    }
}

// ==================== 存储功能测试 ====================
function testSetStorage() {
    const key = document.getElementById('storageKey')?.value;
    const value = document.getElementById('storageValue')?.value;
    
    if (!key) {
        addLog('请填写键名', 'warning');
        return;
    }
    
    try {
        if (typeof UINPlugin.setStorage === 'function') {
            UINPlugin.setStorage(key, value);
            addLog(`保存数据: ${key} = ${value}`, 'success');
        } else {
            addLog('UINPlugin.setStorage 不存在', 'error');
        }
    } catch (e) {
        addLog(`保存失败: ${e.message}`, 'error');
    }
}

function testGetStorage() {
    const key = document.getElementById('storageGetKey')?.value;
    
    if (!key) {
        addLog('请填写键名', 'warning');
        return;
    }
    
    try {
        if (typeof UINPlugin.getStorage === 'function') {
            const value = UINPlugin.getStorage(key);
            const resultSpan = document.getElementById('storageResult');
            if (resultSpan) {
                resultSpan.innerText = value || '(空)';
            }
            addLog(`读取数据: ${key} = ${value}`, 'success');
        } else {
            addLog('UINPlugin.getStorage 不存在', 'error');
        }
    } catch (e) {
        addLog(`读取失败: ${e.message}`, 'error');
    }
}

function testRemoveStorage() {
    const key = document.getElementById('storageGetKey')?.value;
    
    if (!key) {
        addLog('请填写要删除的键名', 'warning');
        return;
    }
    
    try {
        if (typeof UINPlugin.removeStorage === 'function') {
            UINPlugin.removeStorage(key);
            addLog(`删除数据: ${key}`, 'success');
            const resultSpan = document.getElementById('storageResult');
            if (resultSpan) {
                resultSpan.innerText = '(已删除)';
            }
        } else {
            addLog('UINPlugin.removeStorage 不存在', 'error');
        }
    } catch (e) {
        addLog(`删除失败: ${e.message}`, 'error');
    }
}

function testClearStorage() {
    try {
        if (typeof UINPlugin.clearStorage === 'function') {
            UINPlugin.clearStorage();
            addLog(`清空所有存储数据`, 'success');
            const resultSpan = document.getElementById('storageResult');
            if (resultSpan) {
                resultSpan.innerText = '(已清空)';
            }
        } else {
            addLog('UINPlugin.clearStorage 不存在', 'error');
        }
    } catch (e) {
        addLog(`清空失败: ${e.message}`, 'error');
    }
}

function testGetPluginDir() {
    try {
        if (typeof UINPlugin.getPluginDir === 'function') {
            const dir = UINPlugin.getPluginDir();
            addLog(`插件目录: ${dir}`, 'success');
            const resultBox = document.getElementById('pluginDirResult');
            if (resultBox) {
                resultBox.innerHTML = `<strong>插件目录：</strong><br>${dir}`;
            }
        } else {
            addLog('UINPlugin.getPluginDir 不存在', 'error');
        }
    } catch (e) {
        addLog(`获取插件目录失败: ${e.message}`, 'error');
    }
}

// ==================== 系统功能测试 ====================
function testOpenSettings() {
    try {
        if (typeof UINPlugin.openSettings === 'function') {
            UINPlugin.openSettings();
            addLog('打开系统设置', 'info');
        } else {
            addLog('UINPlugin.openSettings 不存在', 'error');
        }
    } catch (e) {
        addLog(`打开系统设置失败: ${e.message}`, 'error');
    }
}

function testOpenAppSettings() {
    try {
        if (typeof UINPlugin.openAppSettings === 'function') {
            UINPlugin.openAppSettings();
            addLog('打开应用设置', 'info');
        } else {
            addLog('UINPlugin.openAppSettings 不存在', 'error');
        }
    } catch (e) {
        addLog(`打开应用设置失败: ${e.message}`, 'error');
    }
}

function testSetTitle() {
    try {
        if (typeof UINPlugin.setTitle === 'function') {
            const newTitle = '测试标题 - ' + new Date().toLocaleTimeString();
            UINPlugin.setTitle(newTitle);
            addLog(`修改标题为: ${newTitle}`, 'success');
        } else {
            addLog('UINPlugin.setTitle 不存在', 'error');
        }
    } catch (e) {
        addLog(`修改标题失败: ${e.message}`, 'error');
    }
}

function testResetTitle() {
    try {
        if (typeof UINPlugin.setTitle === 'function') {
            UINPlugin.setTitle('{{PLUGIN_NAME}}');
            addLog('重置标题为插件名称', 'success');
        } else {
            addLog('UINPlugin.setTitle 不存在', 'error');
        }
    } catch (e) {
        addLog(`重置标题失败: ${e.message}`, 'error');
    }
}

// ==================== 选项卡切换 ====================
function initTabs() {
    const tabs = document.querySelectorAll('.tab-btn');
    const panes = document.querySelectorAll('.tab-pane');
    
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const tabId = tab.getAttribute('data-tab');
            
            tabs.forEach(t => t.classList.remove('active'));
            panes.forEach(p => p.classList.remove('active'));
            
            tab.classList.add('active');
            const activePane = document.getElementById(`tab-${tabId}`);
            if (activePane) activePane.classList.add('active');
            
            addLog(`切换到标签页: ${tab.textContent}`, 'system');
        });
    });
}

// ==================== 生命周期事件 ====================
function initLifecycleEvents() {
    window.addEventListener('resume', () => {
        addLog('插件恢复运行 (resume 事件)', 'success');
    });
    
    window.addEventListener('pause', () => {
        addLog('插件暂停运行 (pause 事件)', 'warning');
    });
    
    window.addEventListener('destroy', () => {
        addLog('插件销毁 (destroy 事件)', 'error');
    });
}

// ==================== 页面初始化 ====================
document.addEventListener('DOMContentLoaded', () => {
    addLog('Web插件测试面板已启动', 'system');
    addLog('{{PLUGIN_NAME}} v{{PLUGIN_VERSION}}', 'system');
    addLog('插件ID: {{PLUGIN_ID}}', 'system');
    
    initTabs();
    initLifecycleEvents();
    
    setTimeout(() => {
        testGetDeviceInfo();
        testGetNetworkInfo();
        testGetTime();
    }, 500);
});

console.log('Web插件测试面板初始化完成');