import {
  CSSProperties,
  DragEvent,
  PointerEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { unavailableReason } from '../../lib/agentAvailability';
import { agentStatus } from '../../lib/agentStatus';
import { layoutCanvas, NODE_H, NODE_W, type CanvasNode } from '../../lib/canvasLayout';
import { isAgentDrag, readAgentDrag } from '../../lib/dnd';
import { useGroupDrop } from '../../lib/useGroupDrop';
import { useAgentDockStore } from '../../store/AgentDockStore';
import { openTerminalWindow } from '../../lib/windowSync';
import shared from '../../styles/shared.module.css';
import type { Agent, Project } from '../../types';
import AgentNodeMenu from './AgentNodeMenu';
import GroupCardMenu from './GroupCardMenu';
import GroupPromptModal from './GroupPromptModal';
import styles from './GroupCanvas.module.css';

interface View {
  x: number;
  y: number;
  scale: number;
}

const MIN_SCALE = 0.3;
const MAX_SCALE = 2;
const FIT_MARGIN = 24;
// 화면 위에 떠 있는 요소(헤더 카드, 명령 패널, 확대/축소 도구)가 가리는 영역. 맞춤은 이 바깥의 빈 곳에 맞춘다.
const INSET_TOP = 72;
const ZOOM_STEP = 1.2;
const FOCUS_MIN_SCALE = 0.85;
const SMOOTH_MS = 450;

const clampScale = (scale: number) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));

/** CSS 변수로 위치/크기를 넘긴다. 모양은 전부 GroupCanvas.module.css 가 정한다. */
const vars = (values: Record<string, string | number>) => values as unknown as CSSProperties;

/**
 * 프로젝트의 에이전트와 그룹을 보여 주는 캔버스. 그룹은 상자, 에이전트는 노드로 그리고 리더에서 멤버로 선을 잇는다.
 * 그룹에 속하지 않은 에이전트는 상자 없이 노드만 놓인다. 배경을 끌어 이동하고 휠로 확대/축소한다.
 * 에이전트 노드를 그룹 상자로 끌어 놓으면 멤버가 되고, 왼쪽 목록으로 끌면 그룹에서 빠진다.
 */
export default function GroupCanvas({
  project,
  insetLeft,
  insetRight,
  insetBottom,
  selectedAgentId,
  focusSeq,
  onSelectAgent,
  onNotice,
}: {
  project: Project;
  /** 선택된 에이전트. 구성도의 해당 노드가 강조된다 */
  selectedAgentId: number | null;
  /** 값이 바뀔 때마다 선택된 에이전트 쪽으로 화면을 옮긴다(같은 에이전트를 다시 눌러도 이동) */
  focusSeq: number;
  onSelectAgent: (agentId: number) => void;
  /** 왼쪽에 떠 있는 에이전트/그룹 패널이 가리는 너비(px) */
  insetLeft: number;
  /** 오른쪽에 떠 있는 명령 패널이 가리는 너비(px) */
  insetRight: number;
  /** 아래쪽 여백(px) */
  insetBottom: number;
  onNotice: (message: string) => void;
}) {
  const {
    agents,
    groups,
    providers,
    roles,
    tasks,
    executions,
    deleteGroup,
    removeGroupMember,
    setGroupLeader,
    setAgentPlaced,
    addGroupMember,
    nodePositions,
    setAgentPosition,
    groupPositions,
    setGroupPosition,
    clearPositions,
  } = useAgentDockStore();
  const dropOnGroup = useGroupDrop(onNotice);
  const projectAgents = useMemo(() => agents.filter((a) => a.projectId === project.id), [agents, project.id]);
  // 구성도에는 "놓인" 에이전트만 그린다. 빼 둔 에이전트는 에이전트 목록에만 있다.
  const placedAgents = useMemo(() => projectAgents.filter((a) => a.placed), [projectAgents]);
  const projectGroups = useMemo(() => groups.filter((g) => g.projectId === project.id), [groups, project.id]);
  const layout = useMemo(
    () => layoutCanvas(placedAgents, projectGroups, nodePositions, groupPositions),
    [placedAgents, projectGroups, nodePositions, groupPositions],
  );

  const [view, setView] = useState<View>({ x: 0, y: 0, scale: 1 });
  const [overKey, setOverKey] = useState<string | null>(null);
  // 노드 위에 버튼을 늘어놓지 않고 "⋮" 메뉴 하나로 모은다.
  const [nodeMenu, setNodeMenu] = useState<{ node: CanvasNode; agent: Agent; rect: DOMRect } | null>(null);
  // 그룹 상자도 "⋮" 메뉴에서 프롬프트 편집/삭제를 한다(왼쪽 그룹 카드와 같다).
  const [boxMenu, setBoxMenu] = useState<{ groupId: number; rect: DOMRect } | null>(null);
  const [promptGroupId, setPromptGroupId] = useState<number | null>(null);
  // 노드나 그룹 상자를 끌어 위치를 옮긴다(노드를 그룹 상자 위에 놓으면 그 그룹으로 들어간다).
  type DragTarget = { kind: 'node'; agentId: number } | { kind: 'group'; groupId: number };
  const [dragTarget, setDragTarget] = useState<DragTarget | null>(null);
  const [dragPos, setDragPos] = useState<{ x: number; y: number } | null>(null);
  const dragRef = useRef<{
    target: DragTarget;
    /** 노드가 속해 있던 그룹(놓을 때 그룹 이동 판단용) */
    fromGroupId: number | null;
    offsetX: number;
    offsetY: number;
    moved: boolean;
    viewX: number;
    viewY: number;
    scale: number;
  } | null>(null);
  // 끌고 난 뒤의 click 은 선택으로 치지 않는다.
  const draggedRef = useRef(false);
  // 선택한 에이전트로 옮길 때만 부드럽게 움직이고, 드래그/휠은 즉시 따라간다.
  const [smooth, setSmooth] = useState(false);
  const smoothTimer = useRef<number | undefined>(undefined);
  const viewportRef = useRef<HTMLDivElement>(null);
  const panRef = useRef<{ px: number; py: number; vx: number; vy: number } | null>(null);
  // 사용자가 직접 이동/확대하기 전까지는 내용이 바뀔 때마다 화면에 맞춘다.
  const touchedRef = useRef(false);

  const fit = useCallback(() => {
    const el = viewportRef.current;
    if (!el) return;
    const { width, height } = el.getBoundingClientRect();
    if (layout.width === 0) {
      setView({ x: 0, y: 0, scale: 1 });
      return;
    }
    const availW = width - insetLeft - insetRight;
    const availH = height - INSET_TOP - insetBottom;
    const scale = Math.min(1, (availW - FIT_MARGIN * 2) / layout.width, (availH - FIT_MARGIN * 2) / layout.height);
    setView({
      scale,
      // 손으로 왼쪽·위로 옮긴 것까지 담도록 경계 상자의 왼쪽 위를 기준으로 맞춘다.
      x: insetLeft + (availW - layout.width * scale) / 2 - layout.minX * scale,
      y: INSET_TOP + (availH - layout.height * scale) / 2 - layout.minY * scale,
    });
  }, [layout.width, layout.height, layout.minX, layout.minY, insetLeft, insetRight, insetBottom]);

  useLayoutEffect(() => {
    if (!touchedRef.current) fit();
  }, [fit]);

  // 카드를 눌렀을 때: 선택한 에이전트의 노드가 보이는 영역(패널/채팅을 뺀 곳) 가운데 오도록 옮긴다.
  useEffect(() => {
    if (focusSeq === 0 || selectedAgentId === null) return;
    const node =
      layout.nodes.find((n) => n.agentId === selectedAgentId && n.groupId === null) ??
      layout.nodes.find((n) => n.agentId === selectedAgentId);
    const el = viewportRef.current;
    if (!node || !el) return;
    const { width, height } = el.getBoundingClientRect();
    const centerX = insetLeft + (width - insetLeft - insetRight) / 2;
    const centerY = INSET_TOP + (height - INSET_TOP - insetBottom) / 2;
    touchedRef.current = true;
    setSmooth(true);
    window.clearTimeout(smoothTimer.current);
    smoothTimer.current = window.setTimeout(() => setSmooth(false), SMOOTH_MS);
    setView((prev) => {
      // 너무 멀리 축소된 상태면 식별할 수 있는 크기까지만 확대한다
      const scale = clampScale(Math.max(prev.scale, FOCUS_MIN_SCALE));
      return {
        scale,
        x: centerX - (node.x + NODE_W / 2) * scale,
        y: centerY - (node.y + NODE_H / 2) * scale,
      };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusSeq]);

  useEffect(() => () => window.clearTimeout(smoothTimer.current), []);

  // 휠은 페이지 스크롤이 아니라 캔버스 확대/축소로 쓴다(preventDefault 가 필요해 passive 로 등록하지 않는다).
  useEffect(() => {
    const el = viewportRef.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      touchedRef.current = true;
      const rect = el.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;
      setView((prev) => {
        const scale = clampScale(prev.scale * Math.exp(-e.deltaY * 0.0015));
        const ratio = scale / prev.scale;
        return { scale, x: cx - (cx - prev.x) * ratio, y: cy - (cy - prev.y) * ratio };
      });
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, []);

  const zoomBy = (factor: number) => {
    const el = viewportRef.current;
    if (!el) return;
    touchedRef.current = true;
    const { width, height } = el.getBoundingClientRect();
    setView((prev) => {
      const scale = clampScale(prev.scale * factor);
      const ratio = scale / prev.scale;
      return { scale, x: width / 2 - (width / 2 - prev.x) * ratio, y: height / 2 - (height / 2 - prev.y) * ratio };
    });
  };

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    if ((e.target as HTMLElement).closest('button, input, [draggable="true"]')) return;
    panRef.current = { px: e.clientX, py: e.clientY, vx: view.x, vy: view.y };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const pan = panRef.current;
    if (!pan) return;
    touchedRef.current = true;
    setView((prev) => ({ ...prev, x: pan.vx + e.clientX - pan.px, y: pan.vy + e.clientY - pan.py }));
  };

  const onPointerUp = () => {
    panRef.current = null;
  };

  const agentById = (id: number) => projectAgents.find((a) => a.id === id);

  const onDragOver = (e: DragEvent, key: string) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    setOverKey(key);
  };

  const onDropOnGroup = (e: DragEvent, groupId: number) => {
    setOverKey(null);
    // 캔버스 전체 드롭(자유 배치)으로 번지지 않게 한다.
    e.stopPropagation();
    dropOnGroup(e, groupId);
  };

  /** 캔버스로 끌어 온 에이전트를 그 자리에 놓는다(미배치는 배치되고, 그룹에 있던 것은 그룹에서 빠진다). */
  const onCanvasDragOver = (e: DragEvent) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
  };

  const onCanvasDrop = (e: DragEvent) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    const payload = readAgentDrag(e);
    if (!payload) return;
    // 그룹 상자 위에 놓인 경우는 상자가 먼저 처리하고 전파를 막는다.
    if (project.masterAgentId !== payload.agentId) {
      groups
        .filter((g) => g.memberIds.includes(payload.agentId))
        .forEach((g) => removeGroupMember(g.id, payload.agentId));
    }
    const world = worldAt(e.clientX, e.clientY, view);
    setAgentPlaced(payload.agentId, true);
    setAgentPosition(payload.agentId, world.x - NODE_W / 2, world.y - NODE_H / 2);
    touchedRef.current = true;
  };

  const onDeleteGroup = (id: number, groupName: string) => {
    if (!window.confirm(`"${groupName}" 그룹을 삭제할까요? 에이전트는 삭제되지 않습니다.`)) return;
    deleteGroup(id);
  };

  /** 화면 좌표를 월드 좌표로 바꾼다. */
  const worldAt = (clientX: number, clientY: number, at: View) => {
    const el = viewportRef.current;
    if (!el) return { x: 0, y: 0 };
    const rect = el.getBoundingClientRect();
    return { x: (clientX - rect.left - at.x) / at.scale, y: (clientY - rect.top - at.y) / at.scale };
  };

  /** 노드나 그룹 상자를 끌기 시작한다. 배경 끌기(이동)와 겹치지 않도록 전파를 막는다. */
  const startDrag = (
    e: PointerEvent<HTMLDivElement>,
    target: DragTarget,
    origin: { x: number; y: number },
    fromGroupId: number | null,
  ) => {
    if (e.button !== 0) return;
    if ((e.target as HTMLElement).closest('button')) return;
    e.stopPropagation();
    // 손으로 옮기기 시작하면 자동 맞춤을 멈춘다(놓을 때 화면이 튀지 않게).
    touchedRef.current = true;
    const world = worldAt(e.clientX, e.clientY, view);
    dragRef.current = {
      target,
      fromGroupId,
      offsetX: world.x - origin.x,
      offsetY: world.y - origin.y,
      moved: false,
      viewX: view.x,
      viewY: view.y,
      scale: view.scale,
    };
    setDragTarget(target);
    setDragPos(origin);
  };

  // 끄는 동안에는 창 전체에서 포인터를 받아, 캔버스 밖으로 나가도 놓치지 않는다.
  useEffect(() => {
    if (dragTarget === null) return;
    const el = viewportRef.current;
    if (!el) return;

    // 끌기 시작할 때 잡아 둔 화면→월드 변환을 그대로 쓴다(끄는 동안에는 화면이 움직이지 않는다).
    const worldOf = (drag: NonNullable<typeof dragRef.current>, clientX: number, clientY: number) => {
      const rect = el.getBoundingClientRect();
      return {
        x: (clientX - rect.left - drag.viewX) / drag.scale,
        y: (clientY - rect.top - drag.viewY) / drag.scale,
      };
    };

    const onMove = (e: globalThis.PointerEvent) => {
      const d = dragRef.current;
      if (!d) return;
      const world = worldOf(d, e.clientX, e.clientY);
      d.moved = true;
      const next = { x: world.x - d.offsetX, y: world.y - d.offsetY };
      setDragPos(next);
      // 그룹은 바로 반영해 상자·노드·선이 함께 따라오게 한다.
      if (d.target.kind === 'group') setGroupPosition(d.target.groupId, next.x, next.y);
    };

    const onUp = (e: globalThis.PointerEvent) => {
      const d = dragRef.current;
      dragRef.current = null;
      setDragTarget(null);
      setDragPos(null);
      if (!d || !d.moved) return;
      draggedRef.current = true;
      // 그룹은 끄는 동안 이미 반영됐다.
      if (d.target.kind === 'group') return;
      const agentId = d.target.agentId;
      const world = worldOf(d, e.clientX, e.clientY);
      const box = layout.boxes.find(
        (b) => world.x >= b.x && world.x <= b.x + b.w && world.y >= b.y && world.y <= b.y + b.h,
      );
      if (box) {
        if (d.fromGroupId === box.groupId) return; // 같은 그룹 안에서 옮긴 것뿐이면 그대로 둔다
        if (project.masterAgentId === agentId) {
          onNotice('마스터는 프로젝트 최상위 리더라 그룹에 넣을 수 없습니다.');
          return;
        }
        addGroupMember(box.groupId, agentId);
        if (d.fromGroupId !== null) removeGroupMember(d.fromGroupId, agentId);
        return;
      }
      // 빈 곳에 놓으면 그룹에서 빠지고 그 자리에 선다.
      groups.filter((g) => g.memberIds.includes(agentId)).forEach((g) => removeGroupMember(g.id, agentId));
      setAgentPosition(agentId, world.x - d.offsetX, world.y - d.offsetY);
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
  }, [
    dragTarget,
    layout.boxes,
    groups,
    project.masterAgentId,
    addGroupMember,
    removeGroupMember,
    setAgentPosition,
    setGroupPosition,
    onNotice,
  ]);

  const promptGroup = projectGroups.find((g) => g.id === promptGroupId) ?? null;
  const isEmpty = placedAgents.length === 0 && projectGroups.length === 0;

  return (
    <section className={styles.canvas}>
      <div
        ref={viewportRef}
        className={styles.viewport}
        style={vars({ '--vx': `${view.x}px`, '--vy': `${view.y}px`, '--vs': view.scale })}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onDragOver={onCanvasDragOver}
        onDrop={onCanvasDrop}
      >
        <div className={`${styles.world} ${smooth ? styles.worldSmooth : ''}`}>
          {layout.boxes.map((box) => {
            const manual = groupPositions[box.groupId];
            const draggingBox = dragTarget?.kind === 'group' && dragTarget.groupId === box.groupId;
            const boxX = draggingBox && dragPos ? dragPos.x : manual ? manual.x : box.x;
            const boxY = draggingBox && dragPos ? dragPos.y : manual ? manual.y : box.y;
            return (
              <div
                key={box.key}
                className={`${styles.box} ${overKey === box.key ? styles.boxOver : ''} ${draggingBox ? styles.boxDragging : ''}`}
                style={vars({ '--x': `${boxX}px`, '--y': `${boxY}px`, '--w': `${box.w}px`, '--h': `${box.h}px` })}
                onDragOver={(e) => onDragOver(e, box.key)}
                onDragLeave={() => setOverKey(null)}
                onDrop={(e) => onDropOnGroup(e, box.groupId)}
              >
                <div
                  className={styles.boxHeader}
                  onPointerDown={(e) =>
                    startDrag(e, { kind: 'group', groupId: box.groupId }, { x: boxX, y: boxY }, null)
                  }
                  title="끌어서 그룹 상자 옮기기"
                >
                  <div className={styles.boxTitles}>
                    <strong className={styles.boxTitle}>{box.title}</strong>
                    <span className={shared.muted}>{box.subtitle}</span>
                  </div>
                  <button
                    type="button"
                    className={styles.boxMenuButton}
                    aria-label={`${box.title} 메뉴`}
                    aria-haspopup="menu"
                    onClick={(e) => {
                      e.stopPropagation();
                      setBoxMenu({ groupId: box.groupId, rect: e.currentTarget.getBoundingClientRect() });
                    }}
                  >
                    ⋮
                  </button>
                </div>
                {box.isEmpty && <div className={styles.emptyDrop}>에이전트를 여기로 끌어 놓으세요</div>}
              </div>
            );
          })}

          <svg className={styles.edges} width={layout.width} height={layout.height} aria-hidden="true">
            {layout.edges.map((edge) => (
              <path key={edge.key} d={edge.d} className={styles.edge} />
            ))}
          </svg>

          {layout.nodes.map((node) => {
            const agent = agentById(node.agentId);
            if (!agent) return null;
            const role = roles.find((r) => r.id === agent.roleId);
            const reason = unavailableReason(agent, providers, project);
            // 노드에도 왼쪽 카드와 같은 상태를 보여 준다(작업 중 / 작업 대기중). 작업 없음은 이름표를 달지 않아 지저분하지 않게 둔다.
            const status = agentStatus(agent, tasks, executions);
            const statusClass =
              status.kind === 'WORKING'
                ? styles.nodeStatusWorking
                : status.kind === 'WAITING'
                  ? styles.nodeStatusWaiting
                  : '';
            const isSelected = node.agentId === selectedAgentId;
            // 끌고 있는 동안에는 포인터를 따라간다.
            const manual = nodePositions[node.agentId];
            const dragging = dragTarget?.kind === 'node' && dragTarget.agentId === node.agentId;
            const nodeX = dragging && dragPos ? dragPos.x : manual ? manual.x : node.x;
            const nodeY = dragging && dragPos ? dragPos.y : manual ? manual.y : node.y;
            return (
              <div
                key={node.key}
                className={`${styles.node} ${node.isLeader ? styles.nodeLeader : ''} ${agent.id === project.masterAgentId ? styles.nodeMaster : ''} ${reason ? styles.nodeUnavailable : ''} ${isSelected ? styles.nodeSelected : ''} ${dragging ? styles.nodeDragging : ''}`}
                aria-current={isSelected ? 'true' : undefined}
                onClick={() => {
                  if (draggedRef.current) {
                    draggedRef.current = false;
                    return;
                  }
                  onSelectAgent(node.agentId);
                }}
                onPointerDown={(e) =>
                  startDrag(e, { kind: 'node', agentId: node.agentId }, { x: nodeX, y: nodeY }, node.groupId)
                }
                style={vars({ '--x': `${nodeX}px`, '--y': `${nodeY}px` })}
                title={reason ?? undefined}
              >
                {node.isLeader && (
                  <span className={styles.leaderMark} title="리더">
                    ★
                  </span>
                )}
                <div className={styles.nodeText}>
                  <span className={styles.nodeName}>{agent.name}</span>
                  <span className={styles.nodeMeta}>
                    {role?.name} · {agent.model || '기본 모델'}
                  </span>
                </div>
                {agent.id === project.masterAgentId && <span className={styles.masterTag}>마스터</span>}
                {status.kind !== 'IDLE' && (
                  <span className={`${styles.nodeStatus} ${statusClass}`} title={`상태: ${status.label}`}>
                    {status.label}
                  </span>
                )}
                {reason && <span className={styles.warnDot} aria-label={reason} />}
                {isSelected && <span className={styles.selectedTag}>선택됨</span>}
                {/* 마스터는 그룹에 속하지 않고 구성도에서 뺄 수도 없어 노드에 할 동작이 없다 */}
                {agent.id !== project.masterAgentId && (
                  <button
                    type="button"
                    className={styles.nodeMenuButton}
                    aria-label={`${agent.name} 메뉴`}
                    aria-haspopup="menu"
                    onClick={(e) => {
                      e.stopPropagation();
                      setNodeMenu({ node, agent, rect: e.currentTarget.getBoundingClientRect() });
                    }}
                  >
                    ⋮
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {isEmpty && (
          <div className={styles.emptyState}>
            <strong>아직 에이전트와 그룹이 없습니다</strong>
            <span>왼쪽에서 새 에이전트를 만들고, 위에서 그룹을 추가해 보세요.</span>
          </div>
        )}
      </div>

      {/* 아래 가운데(패널 사이의 빈 곳 가운데)에 떠 있는 확대/축소 도구 */}
      <div
        className={styles.zoom}
        style={vars({ '--inset-left': `${insetLeft}px`, '--inset-right': `${insetRight}px` })}
      >
        <button type="button" onClick={() => zoomBy(1 / ZOOM_STEP)} aria-label="축소">
          −
        </button>
        <span className={styles.zoomValue}>{Math.round(view.scale * 100)}%</span>
        <button type="button" onClick={() => zoomBy(ZOOM_STEP)} aria-label="확대">
          +
        </button>
        <button
          type="button"
          onClick={() => {
            touchedRef.current = false;
            fit();
          }}
        >
          맞춤
        </button>
        <button
          type="button"
          onClick={() => {
            touchedRef.current = false;
            clearPositions();
            fit();
          }}
          title="손으로 옮긴 위치를 지우고 자동 배치로 되돌립니다"
        >
          위치 초기화
        </button>
      </div>

      {boxMenu && (
        <GroupCardMenu
          anchor={boxMenu.rect}
          onClose={() => setBoxMenu(null)}
          onEditPrompt={() => {
            setPromptGroupId(boxMenu.groupId);
            setBoxMenu(null);
          }}
          onDelete={() => {
            const group = projectGroups.find((g) => g.id === boxMenu.groupId);
            setBoxMenu(null);
            if (group) onDeleteGroup(group.id, group.name);
          }}
        />
      )}
      {promptGroup && <GroupPromptModal group={promptGroup} onClose={() => setPromptGroupId(null)} />}

      {nodeMenu && (
        <AgentNodeMenu
          anchor={nodeMenu.rect}
          inGroup={nodeMenu.node.groupId !== null}
          isLeader={nodeMenu.node.isLeader}
          onClose={() => setNodeMenu(null)}
          onSetLeader={() => {
            const { node } = nodeMenu;
            setNodeMenu(null);
            if (node.groupId !== null) setGroupLeader(node.groupId, node.agentId);
          }}
          onRemoveFromGroup={() => {
            const { node } = nodeMenu;
            setNodeMenu(null);
            if (node.groupId !== null) removeGroupMember(node.groupId, node.agentId);
          }}
          onRemoveFromCanvas={() => {
            const agentId = nodeMenu.agent.id;
            setNodeMenu(null);
            setAgentPlaced(agentId, false);
          }}
          onTerminal={() => {
            openTerminalWindow(nodeMenu.agent.id);
            setNodeMenu(null);
          }}
        />
      )}
    </section>
  );
}
