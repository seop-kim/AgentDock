package com.agent.dock.workspace.util;

import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.dto.FsEntry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 폴더 선택 UI의 최상위 시작 지점(Windows 드라이브 목록 / POSIX 루트)과
 * UNC/네트워크 경로 차단(NTLM 자격 증명 유출 방지)을 담당한다.
 */
@Component
public class WorkspaceFs {

    public List<FsEntry> listRoots() {
        List<FsEntry> roots = new ArrayList<>();
        for (File root : File.listRoots()) {
            String path = root.getAbsolutePath();
            roots.add(new FsEntry(path, path));
        }
        return roots;
    }

    public boolean isUncPath(String path) {
        return path.startsWith("\\\\") || path.startsWith("//");
    }
}
