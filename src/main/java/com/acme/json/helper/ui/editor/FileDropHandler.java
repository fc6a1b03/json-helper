package com.acme.json.helper.ui.editor;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.ui.notice.Notifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.EditorTextField;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 文件拖放处理程序
 * @author 拒绝者
 * @date 2025-01-27
 */
public class FileDropHandler extends DropTargetAdapter {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 编辑器对象
     */
    private final EditorTextField editor;

    public FileDropHandler(final EditorTextField editor) {
        this.editor = editor;
    }

    @Override
    public void drop(final DropTargetDropEvent e) {
        try {
            e.acceptDrop(DnDConstants.ACTION_COPY);
            final Transferable transferable = e.getTransferable();
            // 获取拖放的文件列表
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                // 安全类型检查
                final Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof final List<?> rawList) {
                    // 读取获得的第一个文件并获取该文件的绝对路径
                    final String path = rawList.stream()
                            .filter(File.class::isInstance)
                            .map(File.class::cast)
                            .findFirst()
                            .map(File::getAbsolutePath)
                            .orElse(null);
                    if (StrUtil.isNotEmpty(path)) {
                        // 将正确的路径写回编辑器
                        ApplicationManager.getApplication().invokeLater(() -> editor.setText(path));
                    }
                    e.dropComplete(Boolean.TRUE);
                } else {
                    e.rejectDrop();
                }
            } else {
                e.rejectDrop();
            }
        } catch (final Exception ignored) {
            e.rejectDrop();
            Notifier.notifyError(BUNDLE.getString("file.to.path.warn"), editor.getProject());
        }
    }
}