import type { Agent, AgentGroup } from '../types';

/** 캔버스 월드 좌표 기준 크기(px). 화면 배율은 캔버스의 확대/축소가 따로 곱해진다. */
export const NODE_W = 168;
export const NODE_H = 56;
const GAP = 12;
const PAD = 16;
const HEADER_H = 48;
const ROW_GAP = 28;
const EMPTY_H = 72;
const MIN_BOX_W = 272;
const GROUP_GAP = 32;
const MAX_ROW_W = 1100;
const MEMBER_COLS = 3;
const UNGROUPED_COLS = 2;

export interface CanvasBox {
  key: string;
  groupId: number;
  title: string;
  subtitle: string;
  x: number;
  y: number;
  w: number;
  h: number;
  isEmpty: boolean;
}

export interface CanvasNode {
  key: string;
  agentId: number;
  groupId: number | null;
  isLeader: boolean;
  x: number;
  y: number;
}

export interface CanvasEdge {
  key: string;
  d: string;
}

export interface CanvasLayout {
  boxes: CanvasBox[];
  nodes: CanvasNode[];
  edges: CanvasEdge[];
  width: number;
  height: number;
}

/** 상자 왼쪽 위를 기준으로 한 상대 좌표의 노드. */
interface LocalNode extends Omit<CanvasNode, 'x' | 'y'> {
  lx: number;
  ly: number;
}

interface LocalBox {
  /** 그룹 없는 에이전트 묶음은 상자 없이 노드만 놓이므로 null 이다. */
  box: Omit<CanvasBox, 'x' | 'y'> | null;
  /** 상자가 없어도 배치에는 필요한 크기 */
  w: number;
  h: number;
  nodes: LocalNode[];
  edges: { key: string; x1: number; y1: number; x2: number; y2: number }[];
}

/** 한 줄에 n 개를 가운데 정렬로 놓을 때 i 번째의 왼쪽 x. */
const rowX = (boxW: number, rowCount: number, i: number) => {
  const rowW = rowCount * NODE_W + (rowCount - 1) * GAP;
  return (boxW - rowW) / 2 + i * (NODE_W + GAP);
};

/** 멤버를 cols 열 격자로 놓는다(마지막 줄은 가운데 정렬). startY 는 상자 안 시작 y. */
function placeGrid(agents: Agent[], boxW: number, cols: number, startY: number, base: { groupId: number | null }) {
  const nodes: LocalNode[] = [];
  const rows = Math.ceil(agents.length / cols);
  agents.forEach((agent, index) => {
    const row = Math.floor(index / cols);
    const col = index % cols;
    const inRow = row === rows - 1 ? agents.length - row * cols : cols;
    nodes.push({
      ...base,
      key: `${base.groupId ?? 'none'}-${agent.id}`,
      agentId: agent.id,
      isLeader: false,
      lx: rowX(boxW, inRow, col),
      ly: startY + row * (NODE_H + GAP),
    });
  });
  return { nodes, height: rows === 0 ? 0 : rows * NODE_H + (rows - 1) * GAP };
}

function layoutGroup(group: AgentGroup, agents: Agent[]): LocalBox {
  const members = group.memberIds.map((id) => agents.find((a) => a.id === id)).filter((a): a is Agent => !!a);
  const leader = members.find((a) => a.id === group.leaderAgentId);
  const others = members.filter((a) => a !== leader);
  const cols = Math.max(1, Math.min(MEMBER_COLS, others.length));
  const innerW = Math.max(cols * NODE_W + (cols - 1) * GAP, NODE_W);
  const w = Math.max(innerW + PAD * 2, MIN_BOX_W);

  const nodes: LocalNode[] = [];
  const edges: LocalBox['edges'] = [];
  let y = HEADER_H + PAD;

  if (leader) {
    nodes.push({
      key: `${group.id}-${leader.id}`,
      agentId: leader.id,
      groupId: group.id,
      isLeader: true,
      lx: (w - NODE_W) / 2,
      ly: y,
    });
    y += NODE_H + ROW_GAP;
  }

  const grid = placeGrid(others, w, cols, y, { groupId: group.id });
  nodes.push(...grid.nodes);
  if (leader) {
    grid.nodes.forEach((n) =>
      edges.push({
        key: `${group.id}-${leader.id}-${n.agentId}`,
        x1: w / 2,
        y1: HEADER_H + PAD + NODE_H,
        x2: n.lx + NODE_W / 2,
        y2: n.ly,
      }),
    );
  }

  const contentH = members.length === 0 ? EMPTY_H : leader ? (others.length > 0 ? NODE_H + ROW_GAP + grid.height : NODE_H) : grid.height;
  const h = HEADER_H + PAD + contentH + PAD;
  return {
    box: {
      key: `group-${group.id}`,
      groupId: group.id,
      title: group.name,
      subtitle: `멤버 ${members.length}${leader ? ` · 리더 ${leader.name}` : ''}`,
      w,
      h,
      isEmpty: members.length === 0,
    },
    w,
    h,
    nodes,
    edges,
  };
}

/** 그룹에 속하지 않은 에이전트는 상자나 이름표 없이 노드만 격자로 놓는다. */
function layoutUngrouped(agents: Agent[]): LocalBox {
  const cols = Math.min(UNGROUPED_COLS, agents.length);
  const w = cols * NODE_W + (cols - 1) * GAP;
  const grid = placeGrid(agents, w, cols, 0, { groupId: null });
  return { box: null, w, h: grid.height, nodes: grid.nodes, edges: [] };
}

/**
 * 프로젝트의 에이전트와 그룹을 캔버스 좌표로 배치한다.
 * 그룹에 속하지 않은 에이전트 노드(있을 때)가 맨 앞, 이어서 그룹 상자들이 줄바꿈하며 놓인다.
 * 그룹 안에서는 리더가 위, 나머지 멤버가 아래에 놓이고 리더에서 멤버로 선이 이어진다.
 */
export function layoutCanvas(agents: Agent[], groups: AgentGroup[]): CanvasLayout {
  const groupedIds = new Set(groups.flatMap((g) => g.memberIds));
  const ungrouped = agents.filter((a) => !groupedIds.has(a.id));

  const locals: LocalBox[] = [];
  if (ungrouped.length > 0) locals.push(layoutUngrouped(ungrouped));
  groups.forEach((g) => locals.push(layoutGroup(g, agents)));

  const boxes: CanvasBox[] = [];
  const nodes: CanvasNode[] = [];
  const edges: CanvasEdge[] = [];
  let x = 0;
  let y = 0;
  let rowH = 0;
  let width = 0;

  locals.forEach((local) => {
    if (x > 0 && x + local.w > MAX_ROW_W) {
      x = 0;
      y += rowH + GROUP_GAP;
      rowH = 0;
    }
    if (local.box) boxes.push({ ...local.box, x, y });
    local.nodes.forEach(({ lx, ly, ...node }) => nodes.push({ ...node, x: x + lx, y: y + ly }));
    local.edges.forEach((e) => {
      const x1 = x + e.x1;
      const y1 = y + e.y1;
      const x2 = x + e.x2;
      const y2 = y + e.y2;
      const ym = (y1 + y2) / 2;
      edges.push({ key: e.key, d: `M ${x1} ${y1} C ${x1} ${ym}, ${x2} ${ym}, ${x2} ${y2}` });
    });
    x += local.w + GROUP_GAP;
    rowH = Math.max(rowH, local.h);
    width = Math.max(width, x - GROUP_GAP);
  });

  return { boxes, nodes, edges, width, height: y + rowH };
}
