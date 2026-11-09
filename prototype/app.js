'use strict';

const STORAGE_KEY = 'mbe-gui-prototype-v1';
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

const catalog = [
  { id: 'minecraft:stone', name: 'Stone', glyph: '▦', orientation: '—', geometry: '—', shape: 'cube' },
  { id: 'minecraft:oak_planks', name: 'Oak Planks', glyph: '▤', orientation: '—', geometry: '—', shape: 'cube' },
  { id: 'minecraft:oak_stairs', name: 'Oak Stairs', glyph: '⌑', orientation: 'facing', geometry: 'facing, half, shape', shape: 'stairs' },
  { id: 'minecraft:oak_fence', name: 'Oak Fence', glyph: '╫', orientation: '—', geometry: 'north, east, south, west', shape: 'fence' },
  { id: 'minecraft:oak_door', name: 'Oak Door', glyph: '▥', orientation: 'facing', geometry: 'facing, hinge, open, half', shape: 'door' },
  { id: 'minecraft:composter', name: 'Composter', glyph: '▧', orientation: '—', geometry: 'level', shape: 'composter' },
  { id: 'minecraft:glass_pane', name: 'Glass Pane', glyph: '▥', orientation: '—', geometry: 'north, east, south, west', shape: 'pane' },
  { id: 'minecraft:chest', name: 'Chest', glyph: '▣', orientation: 'facing', geometry: 'facing, type', shape: 'cube' },
  { id: 'minecraft:slime_block', name: 'Slime Block', glyph: '▦', orientation: '—', geometry: '—', shape: 'cube' },
  { id: 'minecraft:honey_block', name: 'Honey Block', glyph: '▦', orientation: '—', geometry: '—', shape: 'cube' },
  { id: 'minecraft:observer', name: 'Observer', glyph: '◉', orientation: 'facing', geometry: 'facing', shape: 'cube' },
  { id: 'minecraft:torch', name: 'Torch', glyph: '♨', orientation: '—', geometry: 'lit', shape: 'thin' },
  { id: 'minecraft:redstone_lamp', name: 'Redstone Lamp', glyph: '▧', orientation: '—', geometry: 'lit', shape: 'cube' },
  { id: 'minecraft:iron_block', name: 'Block of Iron', glyph: '▦', orientation: '—', geometry: '—', shape: 'cube' },
  { id: 'minecraft:oak_slab', name: 'Oak Slab', glyph: '▱', orientation: '—', geometry: 'type', shape: 'slab' },
  { id: 'minecraft:cobblestone_wall', name: 'Cobblestone Wall', glyph: '╬', orientation: '—', geometry: 'north, east, south, west, up', shape: 'wall' }
];

const defaults = {
  movement: 'vanilla',
  ticks: 'vanilla',
  pushCost: 1,
  adhesion: 'vanilla',
  hitbox: { shapeSource: 'outline', minSquare: 3, depth: 2 },
  advanced: {
    shapeSource: 'outline',
    minSquare: 3,
    depth: 2,
    leadDirection: 'auto',
    faces: { F: true, B: true, L: true, R: true, U: true, D: true }
  }
};

let storedConfigs = loadStoredConfigs();
let selectedBlockId = 'minecraft:stone';
let suggestionResults = [];
let activeSuggestion = -1;
let tabSession = null;
let currentView = 'main';
let toastTimeout = null;

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function loadStoredConfigs() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch (error) {
    console.warn('Could not read prototype settings from local storage.', error);
    return {};
  }
}

function mergeConfig(base, override) {
  const output = clone(base);
  if (!override || typeof override !== 'object') return output;
  Object.keys(override).forEach((key) => {
    if (override[key] && typeof override[key] === 'object' && !Array.isArray(override[key])) {
      output[key] = mergeConfig(output[key] || {}, override[key]);
    } else {
      output[key] = override[key];
    }
  });
  return output;
}

function getConfig(id = selectedBlockId) {
  return mergeConfig(defaults, storedConfigs[id] || {});
}

function isDefaultConfig(config) {
  return JSON.stringify(config) === JSON.stringify(defaults);
}

function persistConfigs() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(storedConfigs));
  } catch (error) {
    showToast('Browser storage is unavailable; changes may not persist.');
    console.warn('Could not save prototype settings.', error);
  }
}

function setConfigValue(path, value, id = selectedBlockId) {
  const config = getConfig(id);
  const parts = path.split('.');
  let target = config;
  for (let i = 0; i < parts.length - 1; i += 1) {
    target = target[parts[i]];
  }
  target[parts[parts.length - 1]] = value;
  if (isDefaultConfig(config)) {
    delete storedConfigs[id];
  } else {
    storedConfigs[id] = config;
  }
  persistConfigs();
  updateStatus();
  updateChangedView();
}

function selectedBlock() {
  return catalog.find((block) => block.id === selectedBlockId) || catalog[0];
}

function updateStatus() {
  const config = getConfig();
  const changed = !isDefaultConfig(config);
  const tag = $('#selectedBlockStatus');
  tag.textContent = changed ? 'CUSTOM' : 'DEFAULT';
  tag.classList.toggle('changed', changed);
  $('#changedCount').textContent = String(changedBlockIds().length);
}

function renderMain() {
  const block = selectedBlock();
  const config = getConfig();
  $('#selectedBlockName').textContent = block.name;
  $('#selectedBlockId').textContent = block.id;
  $('#contentBlockName').textContent = block.name;
  $('#contentBlockId').innerHTML = block.id + ' <span class="separator-dot">·</span> Configuration applies to this block only';
  $('#blockGlyph').textContent = block.glyph;
  $('#pushCost').value = String(config.pushCost);

  $$('[data-group="movement"]').forEach((button) => {
    button.classList.toggle('selected', button.dataset.value === config.movement);
    button.setAttribute('aria-pressed', String(button.dataset.value === config.movement));
  });
  $$('[data-group="ticks"]').forEach((button) => {
    button.classList.toggle('selected', button.dataset.value === config.ticks);
    button.setAttribute('aria-pressed', String(button.dataset.value === config.ticks));
  });
  $$('[data-group="adhesion"]').forEach((button) => {
    button.classList.toggle('selected', button.dataset.value === config.adhesion);
    button.setAttribute('aria-pressed', String(button.dataset.value === config.adhesion));
  });

  const configureButton = $('#adhesionConfigure');
  const configureModes = ['hitbox', 'advanced'];
  const shouldConfigure = configureModes.includes(config.adhesion);
  configureButton.hidden = !shouldConfigure;
  configureButton.innerHTML = config.adhesion === 'hitbox'
    ? '<span>Configure hitbox</span><span aria-hidden="true">↗</span>'
    : '<span>Configure advanced</span><span aria-hidden="true">↗</span>';

  const hints = {
    vanilla: "Uses Minecraft's existing adhesion and stickiness behaviour.",
    adhesive: 'All six block faces are configured as adhesive.',
    glazed: 'All six block faces are configured as non-adhesive.',
    hitbox: 'Adhesive faces are determined from the selected block shape and contact thresholds.',
    advanced: 'Use the advanced editor to override geometry analysis and configure each face.'
  };
  $('#adhesionHint').textContent = hints[config.adhesion];
  updateStatus();
}

function selectBlock(id, closeSuggestions = true) {
  const block = catalog.find((item) => item.id === id);
  if (!block) return;
  selectedBlockId = block.id;
  $('#blockSearch').value = block.id;
  if (closeSuggestions) hideSuggestions();
  renderMain();
  if (currentView !== 'main') showView('main');
}

function filteredSuggestions(value) {
  const query = value.trim().toLowerCase();
  if (!query) return [];
  return catalog
    .filter((block) => block.id.toLowerCase().includes(query) || block.name.toLowerCase().includes(query))
    .sort((a, b) => {
      const aExact = a.id.toLowerCase() === query || a.name.toLowerCase() === query;
      const bExact = b.id.toLowerCase() === query || b.name.toLowerCase() === query;
      if (aExact !== bExact) return aExact ? -1 : 1;
      const aPrefix = a.id.toLowerCase().startsWith(query) || a.name.toLowerCase().startsWith(query);
      const bPrefix = b.id.toLowerCase().startsWith(query) || b.name.toLowerCase().startsWith(query);
      if (aPrefix !== bPrefix) return aPrefix ? -1 : 1;
      return a.name.localeCompare(b.name);
    })
    .slice(0, 8);
}

function renderSuggestionList() {
  const input = $('#blockSearch');
  const popup = $('#blockSuggestions');
  popup.innerHTML = '';
  if (!suggestionResults.length) {
    const empty = document.createElement('div');
    empty.className = 'suggestion-empty';
    empty.textContent = 'No matching blocks in the prototype registry.';
    popup.appendChild(empty);
  } else {
    suggestionResults.forEach((block, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'suggestion-row' + (index === activeSuggestion ? ' active' : '');
      button.setAttribute('role', 'option');
      button.setAttribute('aria-selected', String(index === activeSuggestion));
      button.id = 'suggestion-' + index;
      button.innerHTML =
        '<span class="suggestion-copy"><strong>' + escapeHtml(block.id) + '</strong></span>' +
        '<span class="suggestion-meta">' + escapeHtml(block.name) + '</span>';
      button.addEventListener('mousedown', (event) => event.preventDefault());
      button.addEventListener('click', () => selectBlock(block.id));
      popup.appendChild(button);
    });
  }
  popup.hidden = false;
  input.setAttribute('aria-expanded', 'true');
  if (activeSuggestion >= 0 && suggestionResults[activeSuggestion]) {
    input.setAttribute('aria-activedescendant', 'suggestion-' + activeSuggestion);
  } else {
    input.removeAttribute('aria-activedescendant');
  }
}

function renderSuggestions() {
  const value = $('#blockSearch').value;
  suggestionResults = filteredSuggestions(value);
  if (!value.trim()) {
    hideSuggestions();
    return;
  }
  renderSuggestionList();
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function hideSuggestions() {
  $('#blockSuggestions').hidden = true;
  $('#blockSearch').setAttribute('aria-expanded', 'false');
  $('#blockSearch').removeAttribute('aria-activedescendant');
  activeSuggestion = -1;
  tabSession = null;
}

function moveSuggestion(direction) {
  if (!suggestionResults.length) return;
  if (activeSuggestion < 0) {
    activeSuggestion = direction > 0 ? 0 : suggestionResults.length - 1;
  } else {
    activeSuggestion = (activeSuggestion + direction + suggestionResults.length) % suggestionResults.length;
  }
  renderSuggestions();
  const active = $('#suggestion-' + activeSuggestion);
  if (active) active.scrollIntoView({ block: 'nearest' });
}

function acceptSuggestion() {
  const input = $('#blockSearch');
  const isCycling = tabSession && input.value === tabSession.lastFill;
  if (!suggestionResults.length) return;
  if (isCycling && tabSession.results.length) {
    if (activeSuggestion >= 0 && activeSuggestion !== tabSession.index) {
      tabSession.index = activeSuggestion;
    } else {
      tabSession.index = (tabSession.index + 1) % tabSession.results.length;
    }
  } else {
    tabSession = {
      query: input.value,
      results: suggestionResults.slice(),
      index: activeSuggestion >= 0 ? activeSuggestion : 0,
      lastFill: ''
    };
  }
  suggestionResults = tabSession.results;
  activeSuggestion = tabSession.index;
  const chosen = tabSession.results[tabSession.index];
  if (!chosen) return;
  input.value = chosen.id;
  tabSession.lastFill = chosen.id;
  renderSuggestionList();
  input.focus();
}

function commitSearchSelection() {
  const query = $('#blockSearch').value.trim().toLowerCase();
  if (!query) return;
  const highlighted = suggestionResults[activeSuggestion];
  if (highlighted) {
    selectBlock(highlighted.id);
    return;
  }
  const exact = catalog.find((block) => block.id.toLowerCase() === query || block.name.toLowerCase() === query);
  if (exact) {
    selectBlock(exact.id);
    return;
  }
  if (suggestionResults.length === 1) selectBlock(suggestionResults[0].id);
}

function changedBlockIds() {
  return catalog
    .filter((block) => storedConfigs[block.id] && !isDefaultConfig(getConfig(block.id)))
    .map((block) => block.id);
}

function changedLabels(config) {
  const labels = [];
  if (config.movement !== defaults.movement) labels.push('Movement: ' + titleCase(config.movement));
  if (config.ticks !== defaults.ticks) labels.push('Ticks: ' + titleCase(config.ticks));
  if (config.pushCost !== defaults.pushCost) labels.push('Push cost: 1/' + config.pushCost);
  if (config.adhesion !== defaults.adhesion) labels.push('Adhesion: ' + titleCase(config.adhesion));
  return labels;
}

function titleCase(value) {
  return String(value).charAt(0).toUpperCase() + String(value).slice(1);
}

function updateChangedView() {
  const ids = changedBlockIds();
  $('#changedCount').textContent = String(ids.length);
  $('#changedSummary').textContent = ids.length + (ids.length === 1 ? ' custom configuration' : ' custom configurations');
  const list = $('#changedList');
  const empty = $('#emptyChanged');
  list.innerHTML = '';
  empty.hidden = ids.length > 0;
  list.hidden = ids.length === 0;

  ids.forEach((id) => {
    const block = catalog.find((item) => item.id === id);
    if (!block) return;
    const config = getConfig(id);
    const entry = document.createElement('article');
    entry.className = 'changed-entry';
    const glyph = document.createElement('div');
    glyph.className = 'block-glyph';
    glyph.textContent = block.glyph;
    glyph.setAttribute('aria-hidden', 'true');
    const copy = document.createElement('div');
    copy.className = 'changed-entry-copy';
    copy.innerHTML = '<strong>' + escapeHtml(block.name) + '</strong><small>' + escapeHtml(block.id) + '</small>';
    const tags = document.createElement('div');
    tags.className = 'changed-tags';
    const labels = changedLabels(config);
    labels.forEach((label) => {
      const tag = document.createElement('span');
      tag.textContent = label;
      tags.appendChild(tag);
    });
    const actions = document.createElement('div');
    actions.className = 'entry-actions';
    const focus = document.createElement('button');
    focus.type = 'button';
    focus.textContent = 'Focus';
    focus.addEventListener('click', () => {
      selectBlock(id);
      $('#blockSearch').focus();
      $('#blockSearch').select();
    });
    const revert = document.createElement('button');
    revert.type = 'button';
    revert.className = 'revert-entry';
    revert.textContent = 'Revert';
    revert.addEventListener('click', () => {
      delete storedConfigs[id];
      persistConfigs();
      renderMain();
      updateChangedView();
      showToast(block.name + ' restored to defaults.');
    });
    actions.append(focus, revert);
    entry.append(glyph, copy, tags, actions);
    list.appendChild(entry);
  });
}

function showView(view) {
  currentView = view;
  const viewIds = {
    main: 'mainView',
    changed: 'changedView',
    hitbox: 'hitboxView',
    advanced: 'advancedView'
  };
  Object.entries(viewIds).forEach(([key, id]) => {
    const element = document.getElementById(id);
    const visible = key === view;
    element.hidden = !visible;
    element.classList.toggle('active', visible);
  });
  $$('.view-tab').forEach((tab) => {
    const active = tab.dataset.view === view || (view === 'hitbox' || view === 'advanced') && tab.dataset.view === 'main';
    tab.classList.toggle('active', active);
    tab.setAttribute('aria-selected', String(active));
  });
  if (view === 'hitbox') renderHitboxEditor();
  if (view === 'advanced') renderAdvancedEditor();
  if (view === 'changed') updateChangedView();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderHitboxGrid() {
  const grid = $('#hitboxGrid');
  const block = selectedBlock();
  const size = getConfig().hitbox.minSquare;
  const pattern = block.shape;
  grid.innerHTML = '';
  for (let row = 0; row < 16; row += 1) {
    for (let col = 0; col < 16; col += 1) {
      let filled = true;
      if (pattern === 'composter') filled = row < 2 || row > 13 || col < 2 || col > 13;
      if (pattern === 'fence' || pattern === 'wall') filled = row >= 6 && row <= 9 && col >= 6 && col <= 9;
      if (pattern === 'pane' || pattern === 'thin') filled = row >= 7 && row <= 8 || col >= 7 && col <= 8;
      if (pattern === 'slab') filled = row >= 8;
      if (pattern === 'stairs') filled = row >= 8 || (row >= 4 && col >= 8);
      if (pattern === 'door') filled = col >= 2 && col <= 13;
      const cell = document.createElement('span');
      cell.className = 'hitbox-cell' + (filled ? ' filled' : '');
      cell.title = 'Face cell ' + (row + 1) + ', ' + (col + 1) + (filled ? ': illustrative contact' : ': no contact');
      grid.appendChild(cell);
    }
  }
  $('#hitboxMinSquareValue').textContent = size + ' × ' + size + ' px';
}

function renderHitboxEditor() {
  const config = getConfig().hitbox;
  $('#hitboxShapeSource').value = config.shapeSource;
  $('#hitboxMinSquare').value = String(config.minSquare);
  $('#hitboxMinSquareValue').textContent = config.minSquare + ' × ' + config.minSquare + ' px';
  $('#hitboxDepth').value = String(config.depth);
  $('#hitboxDepthValue').textContent = config.depth + ' voxels';
  renderHitboxGrid();
}

function renderAdvancedEditor() {
  const block = selectedBlock();
  const config = getConfig().advanced;
  $('#advancedShapeSource').value = config.shapeSource;
  $('#advancedMinSquare').value = String(config.minSquare);
  $('#advancedMinSquareValue').textContent = config.minSquare + ' × ' + config.minSquare + ' px';
  $('#advancedDepth').value = String(config.depth);
  $('#advancedDepthValue').textContent = config.depth + ' voxels';
  $('#leadDirection').value = config.leadDirection;
  $('#orientationProperties').textContent = block.orientation;
  $('#geometryProperties').textContent = block.geometry;
  $$('.face-toggle').forEach((button) => {
    const enabled = config.faces[button.dataset.face] !== false;
    button.setAttribute('aria-pressed', String(enabled));
  });
}

function applyCost(rawValue) {
  const parsed = Number(rawValue);
  const valid = Number.isInteger(parsed) && parsed >= 1 ? parsed : 1;
  $('#pushCost').value = String(valid);
  setConfigValue('pushCost', valid);
}

function showToast(message) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.hidden = false;
  if (toastTimeout) window.clearTimeout(toastTimeout);
  toastTimeout = window.setTimeout(() => { toast.hidden = true; }, 2300);
}

function initEvents() {
  $$('[data-view]').forEach((button) => {
    button.addEventListener('click', (event) => {
      const view = event.currentTarget.dataset.view;
      if (view) showView(view);
    });
  });

  $$('[data-group]').forEach((button) => {
    button.addEventListener('click', () => {
      setConfigValue(button.dataset.group, button.dataset.value);
      renderMain();
    });
  });

  const search = $('#blockSearch');
  search.addEventListener('input', () => {
    activeSuggestion = -1;
    tabSession = null;
    renderSuggestions();
  });
  search.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if ($('#blockSuggestions').hidden) renderSuggestions();
      moveSuggestion(1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      moveSuggestion(-1);
    } else if (event.key === 'Tab') {
      if (suggestionResults.length) {
        event.preventDefault();
        acceptSuggestion();
      }
    } else if (event.key === 'Enter') {
      event.preventDefault();
      commitSearchSelection();
    } else if (event.key === 'Escape') {
      hideSuggestions();
    }
  });

  $('#clearSearch').addEventListener('click', () => {
    search.value = '';
    hideSuggestions();
    search.focus();
  });

  document.addEventListener('click', (event) => {
    if (!event.target.closest('.autocomplete-wrap')) hideSuggestions();
  });

  $('#costDown').addEventListener('click', () => applyCost(Number($('#pushCost').value) - 1));
  $('#costUp').addEventListener('click', () => applyCost(Number($('#pushCost').value) + 1));
  $('#pushCost').addEventListener('change', () => applyCost($('#pushCost').value));
  $('#pushCost').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') applyCost($('#pushCost').value);
  });

  $('#adhesionConfigure').addEventListener('click', () => {
    const mode = getConfig().adhesion;
    if (mode === 'hitbox') showView('hitbox');
    if (mode === 'advanced') showView('advanced');
  });

  $('#revertCurrent').addEventListener('click', () => {
    const block = selectedBlock();
    delete storedConfigs[selectedBlockId];
    persistConfigs();
    renderMain();
    updateChangedView();
    showToast(block.name + ' restored to defaults.');
  });

  $('#hitboxShapeSource').addEventListener('change', (event) => setConfigValue('hitbox.shapeSource', event.target.value));
  $('#hitboxMinSquare').addEventListener('input', (event) => {
    const value = Number(event.target.value);
    $('#hitboxMinSquareValue').textContent = value + ' × ' + value + ' px';
    setConfigValue('hitbox.minSquare', value);
  });
  $('#hitboxDepth').addEventListener('input', (event) => {
    const value = Number(event.target.value);
    $('#hitboxDepthValue').textContent = value + ' voxels';
    setConfigValue('hitbox.depth', value);
  });
  $('#saveHitbox').addEventListener('click', () => {
    showView('main');
    showToast('Hitbox settings applied to ' + selectedBlock().name + '.');
  });

  $('#advancedShapeSource').addEventListener('change', (event) => setConfigValue('advanced.shapeSource', event.target.value));
  $('#advancedMinSquare').addEventListener('input', (event) => {
    const value = Number(event.target.value);
    $('#advancedMinSquareValue').textContent = value + ' × ' + value + ' px';
    setConfigValue('advanced.minSquare', value);
  });
  $('#advancedDepth').addEventListener('input', (event) => {
    const value = Number(event.target.value);
    $('#advancedDepthValue').textContent = value + ' voxels';
    setConfigValue('advanced.depth', value);
  });
  $('#leadDirection').addEventListener('change', (event) => setConfigValue('advanced.leadDirection', event.target.value));
  $$('.face-toggle').forEach((button) => {
    button.addEventListener('click', () => {
      const face = button.dataset.face;
      const config = getConfig().advanced;
      setConfigValue('advanced.faces.' + face, !config.faces[face]);
      renderAdvancedEditor();
    });
  });
  $('#saveAdvanced').addEventListener('click', () => {
    showView('main');
    showToast('Advanced adhesion settings applied to ' + selectedBlock().name + '.');
  });
}

function init() {
  $('#blockSearch').value = selectedBlockId;
  initEvents();
  renderMain();
  updateChangedView();
  renderSuggestions();
  hideSuggestions();
}

init();
