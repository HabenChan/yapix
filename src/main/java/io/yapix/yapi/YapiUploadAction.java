package io.yapix.yapi;

import static io.yapix.base.NotificationUtils.notifyError;
import static io.yapix.base.NotificationUtils.notifyInfo;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.yapix.action.AbstractAction;
import io.yapix.base.sdk.yapi.YapiClient;
import io.yapix.base.sdk.yapi.model.AuthCookies;
import io.yapix.base.sdk.yapi.model.YapiInterface;
import io.yapix.base.sdk.yapi.response.YapiTestResult.Code;
import io.yapix.config.DefaultConstants;
import io.yapix.config.YapiConfig;
import io.yapix.model.Api;
import io.yapix.yapi.config.YapiSettings;
import io.yapix.yapi.config.YapiSettingsDialog;
import io.yapix.yapi.process.YapiUploader;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 处理Yapi上传入口动作.
 */
public class YapiUploadAction extends AbstractAction {

    @Override
    public boolean before(AnActionEvent event) {
        Project project = event.getData(CommonDataKeys.PROJECT);
        YapiSettings settings = YapiSettings.getInstance();
        if (!settings.isValidate() || Code.OK != settings.testSettings().getCode()) {
            YapiSettingsDialog dialog = YapiSettingsDialog.show(project);
            return !dialog.isCanceled();
        }
        return true;
    }

    @Override
    public void handle(AnActionEvent event, YapiConfig config, List<Api> apis) {
        Project project = event.getData(CommonDataKeys.PROJECT);
        YapiSettings settings = YapiSettings.getInstance();
        YapiClient client = new YapiClient(settings.getUrl(), settings.getAccount(), settings.getPassword(),
                settings.getCookies(), settings.getCookiesTtl());
        Integer projectId = Integer.valueOf(config.getProjectId());

        // 异步处理
        ProgressManager.getInstance().run(new Task.Backgroundable(project, DefaultConstants.NAME) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                double step = 1.0 / apis.size();
                String categoryUrl = null;
                String interfaceUrl = null;
                YapiUploader uploader = new YapiUploader(client);
                try {
                    for (int i = 0; i < apis.size(); i++) {
                        if (indicator.isCanceled()) {
                            break;
                        }
                        Api api = apis.get(i);
                        indicator.setText("[" + (i + 1) + "/" + apis.size() + "] " + api.getMethod() + " "
                                + api
                                .getPath());
                        try {
                            // 上传
                            YapiInterface yapi = uploader.upload(projectId, api);
                            categoryUrl = client.calculateCatUrl(yapi.getProjectId(), yapi.getCatid());
                            interfaceUrl = client.calculateInterfaceUrl(yapi.getProjectId(), yapi.getId());
                        } catch (Exception e) {
                            notifyError("Yapi Upload failed", ExceptionUtils.getStackTrace(e));
                        }
                        indicator.setFraction(indicator.getFraction() + step);
                    }
                    // 保存认证信息
                    AuthCookies cookies = client.getAuthCookies();
                    settings.setCookies(cookies.getCookies());
                    settings.setCookiesTtl(cookies.getTtl());
                } finally {
                    String url = apis.size() == 1 ? interfaceUrl : categoryUrl;
                    if (url != null) {
                        notifyInfo("Yapi Upload successful", String.format("<a href=\"%s\">%s</a>", url, url));
                    }
                }
            }
        });
    }

}