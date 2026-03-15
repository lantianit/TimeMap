/**
 * 微信订阅消息工具
 * 
 * 使用方式：在用户主动操作（点赞、评论、提交举报）后调用 requestSubscribe()
 * 引导用户授权订阅，授权成功后上报给后端记录额度。
 */
const { request } = require('./request');

// 模板ID（需要替换为你在微信后台申请的真实模板ID）
const TEMPLATE_IDS = {
  interaction: 'nIJ0znT39u9-wiX1gR-WFMamM1oh4tJ8hEKPmqmc3uM', // 互动提醒模板ID
  report: 'L74n5hW2b_wOFxmmACVFTbd6oZUTkEs0SJfmR_E5VwQ'       // 举报结果模板ID
};

/**
 * 设置模板ID（可在 app.js 中从配置或接口获取后设置）
 */
function setTemplateIds(ids) {
  if (ids.interaction) TEMPLATE_IDS.interaction = ids.interaction;
  if (ids.report) TEMPLATE_IDS.report = ids.report;
}

/**
 * 请求订阅消息授权
 * @param {string} scene - 场景: 'interaction' | 'report'
 * @returns {Promise}
 */
function requestSubscribe(scene) {
  const templateId = TEMPLATE_IDS[scene];
  if (!templateId) {
    console.log('[subscribe] 模板ID未配置，跳过订阅请求, scene:', scene);
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: [templateId],
      success: (res) => {
        // res[templateId] 可能是 'accept' | 'reject' | 'ban'
        const accepted = [];
        if (res[templateId] === 'accept') {
          accepted.push(templateId);
        }
        if (accepted.length > 0) {
          // 上报给后端记录额度
          request('/notification/subscribe', 'POST', { templateIds: accepted })
            .catch((err) => console.warn('[subscribe] 上报失败', err));
        }
        resolve(res);
      },
      fail: (err) => {
        // 用户拒绝或其他错误，静默处理
        console.log('[subscribe] 授权失败或用户拒绝', err);
        resolve(null);
      }
    });
  });
}

/**
 * 批量请求多个模板的订阅授权
 * @param {string[]} scenes - 场景数组
 */
function requestSubscribeMultiple(scenes) {
  const tmplIds = scenes
    .map(s => TEMPLATE_IDS[s])
    .filter(id => id && id.length > 0);

  if (tmplIds.length === 0) return Promise.resolve();

  return new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds,
      success: (res) => {
        const accepted = tmplIds.filter(id => res[id] === 'accept');
        if (accepted.length > 0) {
          request('/notification/subscribe', 'POST', { templateIds: accepted })
            .catch((err) => console.warn('[subscribe] 上报失败', err));
        }
        resolve(res);
      },
      fail: () => resolve(null)
    });
  });
}

module.exports = {
  TEMPLATE_IDS,
  setTemplateIds,
  requestSubscribe,
  requestSubscribeMultiple
};
