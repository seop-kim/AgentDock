import { DragEvent, FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, ClipIcon, FileIcon } from '../../components/icons';
import { unavailableReason } from '../../lib/agentAvailability';
import { ATTACHMENT_FOLDER, hasAttachment } from '../../lib/attachments';
import { workLog } from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import attach from '../../styles/attachment.module.css';
import shared from '../../styles/shared.module.css';
import type { AttachedFile, ChatTarget, Project } from '../../types';
import AttachFilesModal from './AttachFilesModal';
import styles from './ChatPanel.module.css';
import { ExecutionSummaryCard } from './ExecutionTree';

const toValue = (target: ChatTarget | null) => (target ? `${target.kind}:${target.id}` : '');

const fromValue = (value: string): ChatTarget | null => {
  const [kind, id] = value.split(':');
  return kind === 'agent' || kind === 'group' ? { kind, id: Number(id) } : null;
};

const MAX_INPUT_HEIGHT = 120;

/**
 * 구성도 아래에 떠 있는 채팅 창. 기본 대상은 프로젝트 **마스터 에이전트**이고, 원하면 그룹(리더가 받음)이나
 * 개별 에이전트를 직접 고를 수도 있다. 마스터에게 보내면 마스터가 팀 리더들에게 나눠 맡기고, 자식 실행 결과를 모아 보고한다.
 */
export default function ChatPanel({
  project,
  target,
  onTargetChange,
  open,
  onToggle,
  wide,
  onToggleWide,
  onOpenExecutions,
}: {
  project: Project;
  target: ChatTarget | null;
  onTargetChange: (target: ChatTarget | null) => void;
  /** 패널을 펼쳐 두었는지(접으면 머리말만 남는다). */
  open: boolean;
  onToggle: () => void;
  /** 크게 보기(일시적으로 구성도를 덮고 넓게 쓴다). */
  wide: boolean;
  onToggleWide: () => void;
  /** 응답에 딸린 실행 트리를 연다. */
  onOpenExecutions: (rootExecutionId: number) => void;
}) {
  const { agents, groups, providers, chats, executions, sendCommand, uploadAttachments, chatHasMore, loadMoreChats } =
    useAgentDockStore();
  const [text, setText] = useState('');
  /** 이번 명령에 붙일 파일(보내면 비운다). */
  const [attachments, setAttachments] = useState<AttachedFile[]>([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  /** 파일을 끌어오는 중인지(놓을 곳을 보여 준다). */
  const [dragging, setDragging] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);
  /** 입력 textarea — 줄이 늘면 높이를 맞춘다. */
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const projectAgents = agents.filter((a) => a.projectId === project.id);
  const projectGroups = groups.filter((g) => g.projectId === project.id);
  const projectExecutions = executions.filter((e) => e.projectId === project.id);
  const messages = chats.filter((m) => m.projectId === project.id);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [messages.length, messages.filter((m) => m.status === 'pending').length, open]);

  /** 입력이 길어지면 textarea 높이를 내용에 맞춘다(최대 MAX_INPUT_HEIGHT). */
  useEffect(() => {
    const input = inputRef.current;
    if (!input) return;
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, MAX_INPUT_HEIGHT)}px`;
  }, [text, open]);

  const receiverHint = (() => {
    if (!target) return '명령을 받을 에이전트나 그룹을 고르세요.';
    if (target.kind === 'agent') {
      const agent = projectAgents.find((a) => a.id === target.id);
      if (!agent) return null;
      const reason = unavailableReason(agent, providers, project);
      if (reason) return `${agent.name}: ${reason}`;
      return agent.id === project.masterAgentId
        ? `${agent.name}(마스터)가 받아 팀(그룹) 리더들에게 나눠 맡깁니다.`
        : null;
    }
    const group = projectGroups.find((g) => g.id === target.id);
    const leader = projectAgents.find((a) => a.id === group?.leaderAgentId);
    if (!leader) return `${group?.name} 에는 리더가 없습니다.`;
    const reason = unavailableReason(leader, providers, project);
    return reason ? `리더 ${leader.name}: ${reason}` : `리더 ${leader.name} 이(가) 받습니다.`;
  })();

  /** 끌어온 파일을 복사해 둘 워크스페이스(기본 워크스페이스, 없으면 첫 번째). */
  const defaultWorkspaceId =
    project.workspaces.find((w) => w.isDefault)?.workspaceId ?? project.workspaces[0]?.workspaceId ?? null;

  /**
   * 창 밖에서 끌어온 파일을 붙인다. 에이전트는 워크스페이스 폴더 밖을 볼 수 없으므로
   * 서버가 프로젝트 안 폴더(`ATTACHMENT_FOLDER`)로 **실제로 복사**하고, 그 저장 경로를 첨부로 돌려준다.
   * 폴더는 받지 않는다(파일만).
   */
  const onDrop = async (e: DragEvent<HTMLElement>) => {
    e.preventDefault();
    setDragging(false);
    if (defaultWorkspaceId === null) return;
    const entries = Array.from(e.dataTransfer.items).map((item) => item.webkitGetAsEntry());
    const dropped = Array.from(e.dataTransfer.files).filter((_, index) => entries[index]?.isDirectory !== true);
    if (dropped.length === 0) return;
    const uploaded = await uploadAttachments(defaultWorkspaceId, dropped);
    addUploaded(dropped, uploaded);
  };

  /** 이미지 업로드 실패를 화면에 보여 주기 위한 상태(조용히 실패하지 않게). */
  const [uploadError, setUploadError] = useState<string | null>(null);
  /** 이어갈 작업(이전 실행 트리의 루트 id). 고르면 새 명령 앞에 그 작업 내역을 붙여 보낸다. */
  const [continueFrom, setContinueFrom] = useState<number | null>(null);

  /**
   * 이어갈 작업의 맥락. 그 트리의 **작업 내역**(마스터 요약 + 에이전트별 작업 + 결과)을 짧게 붙여,
   * 에이전트가 "무엇을 이어서 해야 하는지"를 알고 시작하게 한다(이전 결과를 다시 설명할 필요가 없다).
   */
  const continueBlock = (rootId: number): string => {
    const log = workLog(executions, rootId, (agentId) => agents.find((a) => a.id === agentId)?.name ?? '에이전트');
    return `이전 작업(실행 #${rootId})을 이어서 진행해 주세요.\n\n${log === '' ? '(이전 작업 내역을 찾지 못했습니다)' : log}\n\n[새 지시]\n`;
  };
  /** 붙여넣거나 끌어온 이미지의 **미리보기 URL**(보낼 때까지). 서버에 올린 뒤에는 원본 파일이 없어 만들 수 없다. */
  const [previews, setPreviews] = useState<Record<string, string>>({});
  /** 썸네일을 눌러 크게 보고 있는 이미지 URL. */
  const [zoomImage, setZoomImage] = useState<string | null>(null);

  // 크게 보기: Esc 로 닫는다.
  useEffect(() => {
    if (zoomImage === null) return;
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') setZoomImage(null);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [zoomImage]);

  const previewKey = (file: AttachedFile) => `${file.workspaceId}:${file.path}`;

  /** 업로드 결과를 첨부에 더하고, 이미지면 미리보기 URL 을 만들어 둔다(올린 순서가 같다고 본다). */
  const addUploaded = (files: File[], uploaded: AttachedFile[]) => {
    const added: Record<string, string> = {};
    files.forEach((file, index) => {
      const created = uploaded[index];
      if (created === undefined || !file.type.startsWith('image/')) return;
      added[`${created.workspaceId}:${created.path}`] = URL.createObjectURL(file);
    });
    setPreviews((prev) => ({ ...prev, ...added }));
    setAttachments((prev) => [...prev, ...uploaded.filter((file) => !hasAttachment(prev, file))]);
  };

  /** 첨부를 뺄 때 미리보기 URL 도 함께 정리한다(메모리에 남지 않게). */
  const removeAttachment = (file: AttachedFile) => {
    setAttachments((prev) => prev.filter((item) => item !== file));
    setPreviews((prev) => {
      const url = prev[previewKey(file)];
      if (url !== undefined) URL.revokeObjectURL(url);
      const next = { ...prev };
      delete next[previewKey(file)];
      return next;
    });
  };

  /**
   * 클립보드의 이미지를 첨부로 올린다(드롭·파일 고르기와 같은 경로).
   * 이미지가 아니면 아무것도 하지 않아 글자 붙여넣기가 그대로 동작한다. 처리했으면 true.
   */
  const attachPastedImages = async (clipboard: DataTransfer | null): Promise<boolean> => {
    if (clipboard === null || defaultWorkspaceId === null) return false;
    const files = Array.from(clipboard.items)
      // type 이 빈 값으로 오는 앱도 있다 — 그때도 파일이면 이미지로 본다.
      .filter((item) => item.kind === 'file' && (item.type === '' || item.type.startsWith('image/')))
      .map((item) => item.getAsFile())
      .filter((file): file is File => file !== null);
    if (files.length === 0) return false;

    // 붙여넣은 이미지는 파일 이름이 없다 — 알아볼 수 있게 시각으로 이름을 붙인다.
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const named = files.map((file, index) => {
      const extension = file.type === '' ? 'png' : (file.type.split('/')[1] ?? 'png');
      const suffix = index === 0 ? '' : `-${index + 1}`;
      return new File([file], `붙여넣은-이미지-${stamp}${suffix}.${extension}`, { type: file.type });
    });
    try {
      const uploaded = await uploadAttachments(defaultWorkspaceId, named);
      addUploaded(named, uploaded);
      setUploadError(null);
    } catch (error) {
      setUploadError(
        `이미지를 붙이지 못했습니다: ${error instanceof Error ? error.message : '알 수 없는 오류'}`,
      );
    }
    return true;
  };

  // 패널이 펼쳐져 있으면 **포커스가 어디에 있든** 붙여넣기를 받는다(캔버스를 눌러 둔 채 붙여넣는 경우가 흔하다).
  useEffect(() => {
    if (!open) return;
    const onDocumentPaste = (e: ClipboardEvent) => {
      // 진단용(debug 레벨): 어떤 형식으로 오는지 남긴다 — 이미지가 안 잡힐 때 원인을 바로 알기 위해.
      console.debug(
        '[chat] paste items',
        Array.from(e.clipboardData?.items ?? []).map((item) => `${item.kind}:${item.type}`),
      );
      void attachPastedImages(e.clipboardData).then((handled) => {
        if (handled) e.preventDefault();
      });
    };
    document.addEventListener('paste', onDocumentPaste);
    return () => document.removeEventListener('paste', onDocumentPaste);
    // attachPastedImages 는 매 렌더 새로 만들어지지만, 안에서 쓰는 값은 아래 deps 로 충분하다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, defaultWorkspaceId]);

  const onDragOver = (e: DragEvent<HTMLElement>) => {
    e.preventDefault();
    setDragging(true);
  };

  const onDragLeave = (e: DragEvent<HTMLElement>) => {
    // 자식 요소로 옮겨 다닐 때도 dragleave 가 오므로, 창 밖으로 나갈 때만 끈다.
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setDragging(false);
  };

  /** Enter 단독이면 전송, Shift+Enter 는 줄바꿈으로 둔다. */
  const onInputKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      e.currentTarget.form?.requestSubmit();
    }
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!target) return;
    const trimmed = text.trim();
    // 파일만 붙이고 보내도 되게 한다(그때는 지시를 기본 문장으로 채운다).
    if (trimmed === '' && attachments.length === 0) return;
    const body = trimmed === '' ? '첨부한 파일을 확인해줘' : trimmed;
    sendCommand(
      project.id,
      target,
      continueFrom === null ? body : `${continueBlock(continueFrom)}${body}`,
      attachments,
    );
    setText('');
    setContinueFrom(null);
    // 보낸 첨부의 미리보기 URL 은 더 쓸 일이 없으니 정리한다.
    Object.values(previews).forEach((url) => URL.revokeObjectURL(url));
    setPreviews({});
    setAttachments([]);
  };

  /** 입력창 높이(px). 위쪽 손잡이를 **위로** 끌면 커진다(아래는 화면에 붙어 있어 아래로는 못 늘린다). */
  const [inputHeight, setInputHeight] = useState(38);

  const startResize = (e: React.PointerEvent) => {
    e.preventDefault();
    const startY = e.clientY;
    const startHeight = inputHeight;
    const move = (event: PointerEvent) => {
      const next = startHeight + (startY - event.clientY);
      setInputHeight(Math.max(38, Math.min(next, Math.round(window.innerHeight * 0.7))));
    };
    const stop = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', stop);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', stop);
  };

  return (
    <section
      className={`${styles.chat} ${wide ? styles.wide : ''} ${open ? '' : styles.collapsed} ${
        dragging ? styles.dropTarget : ''
      }`}
      aria-label="에이전트 명령"
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      {/* 명령 박스 왼쪽 테두리에 걸친 넓히기 화살표(좌우로 넓힌다) */}
      {open && (
        <button
          type="button"
          className={styles.wideButton}
          onClick={onToggleWide}
          aria-label={wide ? '원래 크기로' : '크게 보기'}
          title={wide ? '원래 크기로' : '크게 보기'}
        >
          {wide ? <ChevronRightIcon size={16} /> : <ChevronLeftIcon size={16} />}
        </button>
      )}

      <div className={styles.header}>
        <button
          type="button"
          className={styles.fold}
          onClick={onToggle}
          aria-expanded={open}
          aria-label={open ? '명령 패널 접기' : '명령 패널 펴기'}
          title={open ? '명령 패널 접기' : '명령 패널 펴기'}
        >
          <ChevronDownIcon />
        </button>
        <h2 className={styles.title}>명령</h2>
        {!open && messages.length > 0 && <span className={styles.count}>기록 {messages.length}</span>}
      </div>

      {open && (
        <>
        <select
          className={styles.target}
          value={toValue(target)}
          onChange={(e) => onTargetChange(fromValue(e.target.value))}
          aria-label="명령 대상"
        >
          <option value="">대상 선택</option>
          <optgroup label="에이전트">
            {projectAgents.map((a) => (
              <option key={a.id} value={`agent:${a.id}`}>
                {a.name}
                {a.id === project.masterAgentId ? ' (마스터)' : ''}
              </option>
            ))}
          </optgroup>
          <optgroup label="그룹 (리더가 받음)">
            {projectGroups.map((g) => (
              <option key={g.id} value={`group:${g.id}`}>
                {g.name}
              </option>
            ))}
          </optgroup>
        </select>
        {receiverHint && <span className={styles.hint}>{receiverHint}</span>}

        <div ref={logRef} className={styles.log}>
          {/* 오래된 명령은 한 페이지(5개)씩만 읽는다 — 더 있으면 여기서 이어 읽는다. */}
          {chatHasMore[project.id] === true && (
            <button type="button" className={styles.loadMore} onClick={() => loadMoreChats(project.id)}>
              이전 명령 더 보기
            </button>
          )}
          {messages.length === 0 && <p className={shared.muted}>아직 보낸 명령이 없습니다.</p>}
          {messages.map((m) => {
            const rootId = m.rootExecutionId;
            // 사람이 답할 것은 채팅이 아니라 "내가 처리할 요청"(또는 캔버스 말풍선)에서 처리한다 — 채팅에는 안 띄운다.
            return (
              <div key={m.id} className={`${styles.message} ${styles[m.role]}`}>
                <div className={styles.meta}>
                  <strong>{m.author}</strong>
                  {m.targetLabel && <span> → {m.targetLabel}</span>}
                </div>
                <div className={m.status === 'error' ? styles.error : undefined}>
                  {m.status === 'pending' && m.role !== 'user' ? (
                    <span className={styles.pending}>작업 중…</span>
                  ) : (
                    <div className={styles.markdown}>
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.text}</ReactMarkdown>
                    </div>
                  )}
                </div>
                {m.attachments.length > 0 && (
                  <ul className={attach.chips}>
                    {m.attachments.map((file) => (
                      <li key={`${file.workspaceId}:${file.path}`} className={attach.chip} title={file.path}>
                        <FileIcon size={13} />
                        <span className={attach.chipName}>{file.name}</span>
                      </li>
                    ))}
                  </ul>
                )}
                {/* 실행 현황은 에이전트 응답에만 붙인다 — 내가 보낸 말에는 상태를 달지 않는다. */}
                {rootId !== null && m.role !== 'user' && (
                  <ExecutionSummaryCard
                    executions={projectExecutions}
                    rootExecutionId={rootId}
                    onOpen={() => onOpenExecutions(rootId)}
                  />
                )}
              </div>
            );
          })}
        </div>

      {attachments.length > 0 && (
        <ul className={attach.chips}>
          {attachments.map((file) => (
            <li key={`${file.workspaceId}:${file.path}`} className={attach.chip} title={file.path}>
              {/* 붙여넣거나 끌어온 이미지는 미리보기를 보여 준다(서버에서 다시 받아온 첨부는 아이콘만). */}
              {previews[previewKey(file)] === undefined ? (
                <FileIcon size={13} />
              ) : (
                <img
                  className={styles.chipThumb}
                  src={previews[previewKey(file)]}
                  alt={file.name}
                  title="눌러서 크게 보기"
                  onClick={() => setZoomImage(previews[previewKey(file)] ?? null)}
                />
              )}
              <span className={attach.chipName}>{file.name}</span>
              <button
                type="button"
                className={attach.chipRemove}
                onClick={() => removeAttachment(file)}
                aria-label={`${file.name} 첨부 빼기`}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}

      {dragging && (
        <p className={styles.dropHint}>
          {defaultWorkspaceId === null ? (
            '워크스페이스가 없어 파일을 붙일 수 없습니다.'
          ) : (
            <>
              여기에 놓으면 프로젝트 폴더(<code>{ATTACHMENT_FOLDER}</code>)로 복사해 붙입니다.
            </>
          )}
        </p>
      )}

      <form onSubmit={onSubmit} className={styles.form}>
        {/* 위쪽 손잡이: 위로 끌면 입력창이 커진다(아래쪽에 두면 화면 끝에 걸려 한계가 있었다). */}
        <div
          className={styles.resizeBar}
          role="separator"
          aria-label="입력창 높이 조절"
          aria-orientation="horizontal"
          title="위로 끌면 입력창이 커집니다"
          onPointerDown={startResize}
        />
        {/* 이어갈 작업 고르기 — 고르면 새 명령 앞에 그 작업의 내역을 붙여 보낸다. */}
        <select
          className={styles.continuePick}
          value={continueFrom === null ? '' : String(continueFrom)}
          onChange={(e) => setContinueFrom(e.target.value === '' ? null : Number(e.target.value))}
          aria-label="이어갈 작업"
          title="이어갈 작업을 고르면 그 작업 내역을 함께 보냅니다"
        >
          <option value="">이어가기 없음</option>
          {chats
            .filter(
              (message) =>
                message.projectId === project.id &&
                message.role === 'user' &&
                message.rootExecutionId !== null,
            )
            .slice(-10)
            .reverse()
            .map((message) => (
              <option key={message.id} value={String(message.rootExecutionId)}>
                #{message.rootExecutionId} {message.text.slice(0, 26)}
              </option>
            ))}
        </select>
        <button
          type="button"
          className={styles.attach}
          onClick={() => setPickerOpen(true)}
          disabled={project.workspaces.length === 0}
          aria-label="파일 첨부"
          title={
            project.workspaces.length === 0
              ? '워크스페이스가 없어 파일을 붙일 수 없습니다'
              : '파일 첨부 (창에 끌어다 놓아도 됩니다)'
          }
        >
          <ClipIcon size={18} />
        </button>
        <textarea
          ref={inputRef}
          rows={1}
          style={{ height: `${inputHeight}px` }}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onInputKeyDown}
          placeholder={target ? '명령을 입력하고 Enter (Shift+Enter 로 줄바꿈)' : '먼저 대상을 선택하세요'}
          aria-label="명령 입력"
        />
        <button type="submit" disabled={!target || (text.trim() === '' && attachments.length === 0)}>
          보내기
        </button>
        </form>
        {uploadError && <p className={styles.uploadError}>{uploadError}</p>}
        </>
      )}

      {pickerOpen && (
        <AttachFilesModal
          project={project}
          attached={attachments}
          onClose={() => setPickerOpen(false)}
          onApply={(files) => {
            setAttachments(files);
            setPickerOpen(false);
          }}
        />
      )}

      {/* 썸네일을 누르면 뜨는 큰 미리보기 — 어느 이미지인지 확실히 알아보기 위해. 클릭·Esc 로 닫는다. */}
      {zoomImage !== null && (
        <div
          className={styles.lightbox}
          role="dialog"
          aria-label="첨부 이미지 크게 보기"
          onClick={() => setZoomImage(null)}
        >
          <img className={styles.lightboxImage} src={zoomImage} alt="첨부 이미지" />
        </div>
      )}
    </section>
  );
}
