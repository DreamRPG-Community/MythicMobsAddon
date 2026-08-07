(() => {
    const state = {
        page: 0,
        pageSize: 45,
        total: 0,
        visibleItems: [],
        selectedItems: new Map(),
        revision: '',
        current: '',
        authToken: '',
        creating: false,
        configuration: {},
        editorCatalog: {materials: [], enchantments: [], itemFlags: [], taxonomy: {tags: []}},
        enchantments: new Map(),
        flags: new Set(),
        selectedTags: new Set(),
        materialName: 'STONE',
        materialData: 0,
        taxonomyEditor: null,
        taxonomyPage: 0,
        taxonomyPageSize: 8,
        taxonomyColor: '#929394',
        importFiles: [],
        importResult: null,
        importBusy: false
    };

    const richSelections = new WeakMap();
    const richHistories = new WeakMap();
    const richHistoryLimit = 100;
    const richInputGroupDelay = 700;

    const $ = id => document.getElementById(id);
    const taxonomy = () => {
        const value = state.editorCatalog?.taxonomy;
        return value && Array.isArray(value.tags) ? value : {tags: []};
    };
    const tagMap = () => new Map(taxonomy().tags
        .filter(entry => entry && entry.id != null)
        .map(entry => [entry.id, entry]));

    const enchantmentNames = {
        ARROW_DAMAGE: '力量',
        ARROW_FIRE: '火矢',
        ARROW_INFINITE: '无限',
        ARROW_KNOCKBACK: '冲击',
        BINDING_CURSE: '绑定诅咒',
        DAMAGE_ALL: '锋利',
        DAMAGE_ARTHROPODS: '节肢杀手',
        DAMAGE_UNDEAD: '亡灵杀手',
        DEPTH_STRIDER: '深海探索者',
        DIG_SPEED: '效率',
        DURABILITY: '耐久',
        FIRE_ASPECT: '火焰附加',
        FROST_WALKER: '冰霜行者',
        KNOCKBACK: '击退',
        LOOT_BONUS_BLOCKS: '时运',
        LOOT_BONUS_MOBS: '抢夺',
        LURE: '饵钓',
        MENDING: '经验修补',
        OXYGEN: '水下呼吸',
        PROTECTION_ENVIRONMENTAL: '保护',
        PROTECTION_EXPLOSIONS: '爆炸保护',
        PROTECTION_FALL: '摔落保护',
        PROTECTION_FIRE: '火焰保护',
        PROTECTION_PROJECTILE: '弹射物保护',
        SILK_TOUCH: '精准采集',
        SWEEPING_EDGE: '横扫之刃',
        THORNS: '荆棘',
        VANISHING_CURSE: '消失诅咒',
        WATER_WORKER: '水下速掘',
        LUCK: '海之眷顾'
    };

    const itemFlagNames = {
        HIDE_ATTRIBUTES: '隐藏属性',
        HIDE_DESTROYS: '隐藏可破坏方块',
        HIDE_ENCHANTS: '隐藏附魔',
        HIDE_PLACED_ON: '隐藏可放置方块',
        HIDE_POTION_EFFECTS: '隐藏药水效果',
        HIDE_UNBREAKABLE: '隐藏不可破坏'
    };

    const minecraftColorValues = {
        '&0': '#000000',
        '&1': '#0000aa',
        '&2': '#00aa00',
        '&3': '#00aaaa',
        '&4': '#aa0000',
        '&5': '#aa00aa',
        '&6': '#ffaa00',
        '&7': '#aaaaaa',
        '&9': '#5555ff',
        '&a': '#55ff55',
        '&b': '#55ffff',
        '&c': '#ff5555',
        '&d': '#ff55ff',
        '&e': '#ffff55',
        '&f': '#ffffff'
    };

    const minecraftColorDisplayValues = {
        '#000000': '#c8c8c8',
        '#0000aa': '#7070ff',
        '#00aa00': '#55aa55',
        '#00aaaa': '#55aaaa',
        '#aa0000': '#ff7373',
        '#aa00aa': '#aa55aa',
        '#ffaa00': '#ffb83d',
        '#aaaaaa': '#c8c8c8',
        '#5555ff': '#5555ff',
        '#55ff55': '#55ff55',
        '#55ffff': '#55ffff',
        '#ff5555': '#ff5555',
        '#ff55ff': '#ff55ff',
        '#ffff55': '#ffff55',
        '#ffffff': '#ffffff'
    };

    const normalizeHexColor = color => {
        const value = String(color || '').trim();
        if (/^#[0-9a-f]{6}$/i.test(value)) return value.toLowerCase();
        if (/^#[0-9a-f]{3}$/i.test(value)) {
            return `#${value[1]}${value[1]}${value[2]}${value[2]}${value[3]}${value[3]}`.toLowerCase();
        }
        return '#929394';
    };

    const tagBackground = color => {
        const value = normalizeHexColor(color).slice(1);
        const red = Number.parseInt(value.slice(0, 2), 16);
        const green = Number.parseInt(value.slice(2, 4), 16);
        const blue = Number.parseInt(value.slice(4, 6), 16);
        const luminance = (red * 0.299 + green * 0.587 + blue * 0.114) / 255;
        if (red + green + blue < 24) return 'rgba(255, 255, 255, 0.12)';
        return `rgba(${red}, ${green}, ${blue}, ${luminance < 0.2 ? 0.28 : 0.18})`;
    };

    const readableTagColor = color => {
        const normalized = normalizeHexColor(color);
        const preset = minecraftColorDisplayValues[normalized];
        if (preset) return preset;
        const value = normalized.slice(1);
        const channels = [
            Number.parseInt(value.slice(0, 2), 16),
            Number.parseInt(value.slice(2, 4), 16),
            Number.parseInt(value.slice(4, 6), 16)
        ];
        const luminance = (channels[0] * 0.299 + channels[1] * 0.587 + channels[2] * 0.114) / 255;
        if (luminance >= 0.32) return normalized;
        return `#${channels.map(channel => Math.round(channel + (255 - channel) * 0.62)
            .toString(16).padStart(2, '0')).join('')}`;
    };

    const applyTagVisual = (element, color) => {
        const value = normalizeHexColor(color);
        const textColor = readableTagColor(value);
        element.style.setProperty('--tag-color', textColor);
        element.style.color = textColor;
        element.style.backgroundColor = tagBackground(value);
    };

    const enchantmentDisplayName = entry => enchantmentNames[entry.key] || entry.displayName || entry.key;
    const itemFlagDisplayName = flag => itemFlagNames[flag] || `隐藏信息（${flag}）`;

    const request = async (path, options = {}) => {
        const headers = new Headers(options.headers || {});
        headers.set('X-MythicMobsAddon-Token', state.authToken);
        if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
            headers.set('Content-Type', 'application/json');
        }
        const response = await fetch(path, {...options, headers});
        const body = await response.json().catch(() => ({}));
        if (!response.ok) {
            const error = new Error(body.error?.message || body.message || `HTTP ${response.status}`);
            error.status = response.status;
            throw error;
        }
        return body;
    };

    const setMessage = (value, error = false) => {
        $('form-message').textContent = error ? value : '';
        $('form-message').classList.toggle('error', error);
        $('form-message').classList.toggle('success', !error && Boolean(value));
    };

    const showToast = message => {
        if (!message) return;
        const toast = document.createElement('div');
        toast.className = 'toast success';
        toast.setAttribute('role', 'status');
        toast.textContent = message;
        $('toast-stack').append(toast);
        window.setTimeout(() => {
            toast.classList.add('leaving');
            window.setTimeout(() => toast.remove(), 180);
        }, 2400);
    };

    const showError = error => {
        if (document.body.classList.contains('locked')) return;
        setMessage(error.message || String(error), true);
    };

    const resizeTextArea = field => {
        if (!field) return;
        field.style.height = 'auto';
        field.style.height = `${Math.max(field.scrollHeight, 60)}px`;
    };

    const resizeConfiguration = () => {
        const configuration = $('configuration');
        resizeTextArea(configuration);
        resizeTextArea($('attributes-json'));
        resizeTextArea($('options-json'));
        resizeTextArea($('nbt-json'));
        const snapshot = $('raw-yaml');
        if (configuration && snapshot) snapshot.style.height = `${configuration.offsetHeight}px`;
    };

    const iconCandidates = (materialName, data = 0) => {
        const name = String(materialName || '').toLowerCase();
        const base = 'https://assets.mcasset.cloud/1.12.2/assets/minecraft/textures/';
        const result = [];
        if (name === 'wool' && Number(data) >= 0 && Number(data) < 16) {
            const colors = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'];
            result.push(`${base}blocks/wool_colored_${colors[Number(data)]}.png`);
        }
        if (name === 'ink_sack' || name === 'dye') result.push(`${base}items/dye_powder.png`);
        const itemLike = /(_sword|_pickaxe|_axe|_shovel|_hoe|_helmet|_chestplate|_leggings|_boots|_ingot|_nugget|_gem|_bucket|_spawn_egg)$/.test(name)
            || ['apple', 'bread', 'carrot', 'potato', 'stick', 'bow', 'arrow', 'book', 'paper', 'shears', 'flint', 'coal', 'diamond', 'emerald', 'golden_apple', 'fishing_rod', 'leather', 'string', 'feather', 'egg', 'snowball', 'slime_ball'].includes(name);
        if (itemLike) result.push(`${base}items/${name}.png`);
        result.push(`${base}blocks/${name}.png`, `${base}items/${name}.png`);
        return [...new Set(result)];
    };

    const setIcon = (container, urls, fallback = 'MM', alt = '') => {
        container.replaceChildren();
        container.classList.add('missing');
        const image = document.createElement('img');
        image.alt = alt;
        let index = 0;
        const useNext = () => {
            if (index >= urls.length) {
                container.classList.add('missing');
                container.innerHTML = `<span class="icon-fallback"></span>`;
                container.querySelector('.icon-fallback').textContent = fallback;
                return;
            }
            container.classList.remove('missing');
            image.src = urls[index++];
        };
        image.onerror = useNext;
        image.onload = () => container.classList.remove('missing');
        container.append(image);
        useNext();
    };

    const mcClasses = {
        color: 'mc-color-',
        bold: 'mc-bold',
        italic: 'mc-italic',
        underline: 'mc-underline',
        strike: 'mc-strike',
        obfuscated: 'mc-obfuscated'
    };
    const isMinecraftCode = code => /^[0-9a-fk-or]$/i.test(code);
    const isMinecraftColor = code => /^[0-9a-f]$/i.test(code);
    const minecraftStyle = color => ({
        color,
        bold: false,
        italic: false,
        underline: false,
        strike: false,
        obfuscated: false
    });

    const normalizeMinecraftText = value => {
        const source = String(value ?? '').replace(/§/g, '&');
        let result = '';
        let lastColorIndex = -1;
        let codeRunStart = 0;
        for (let index = 0; index < source.length; index++) {
            const character = source[index];
            if (character !== '&' || index + 1 >= source.length || !isMinecraftCode(source[index + 1])) {
                result += character;
                lastColorIndex = -1;
                codeRunStart = result.length;
                continue;
            }
            const code = source[++index].toLowerCase();
            if (code === 'r') {
                result += `&${code}`;
                lastColorIndex = -1;
                codeRunStart = result.length;
                continue;
            }
            if (isMinecraftColor(code)) {
                if (lastColorIndex >= codeRunStart) result = result.slice(0, lastColorIndex) + result.slice(lastColorIndex + 2);
                lastColorIndex = result.length;
            }
            result += `&${code}`;
        }
        return result;
    };

    const appendMinecraftText = (parent, text, preserveFormatting = false) => {
        const source = normalizeMinecraftText(text);
        const matcher = /&([0-9a-fk-or])/gi;
        let cursor = 0;
        let pendingPrefix = '';
        let style = minecraftStyle('f');
        const appendPart = (value, prefix = '', preserveEmpty = false) => {
            if (!value && !(preserveFormatting && preserveEmpty && prefix)) return;
            const span = document.createElement('span');
            span.textContent = value;
            if (preserveFormatting && prefix) span.dataset.mcPrefix = prefix;
            span.classList.add(mcClasses.color + style.color);
            if (style.bold) span.classList.add(mcClasses.bold);
            if (style.italic) span.classList.add(mcClasses.italic);
            if (style.underline) span.classList.add(mcClasses.underline);
            if (style.strike) span.classList.add(mcClasses.strike);
            if (style.obfuscated) span.classList.add(mcClasses.obfuscated);
            parent.append(span);
        };
        let match;
        while ((match = matcher.exec(source)) !== null) {
            const before = source.slice(cursor, match.index);
            appendPart(before, pendingPrefix);
            if (before) pendingPrefix = '';
            pendingPrefix += match[0];
            const code = match[1].toLowerCase();
            if (/^[0-9a-f]$/.test(code)) style = minecraftStyle(code);
            else if (code === 'l') style.bold = true;
            else if (code === 'k') style.obfuscated = true;
            else if (code === 'm') style.strike = true;
            else if (code === 'n') style.underline = true;
            else if (code === 'o') style.italic = true;
            else style = minecraftStyle('f');
            cursor = matcher.lastIndex;
        }
        appendPart(source.slice(cursor), pendingPrefix, true);
    };

    const renderMinecraft = (parent, text) => {
        parent.replaceChildren();
        appendMinecraftText(parent, text);
    };

    const renderRichEditor = (element, value) => {
        element.replaceChildren();
        appendMinecraftText(element, value, true);
    };

    const serializeRichEditor = element => {
        const output = [];
        const isBlock = tagName => ['DIV', 'LI', 'P'].includes(tagName);
        const endsWithNewline = () => output.length > 0 && String(output[output.length - 1]).endsWith('\n');
        const walk = (node, inheritedPrefix = '') => {
            if (node.nodeType === 3) {
                const value = String(node.nodeValue || '').replace(/\u00a0/g, ' ').replace(/\r\n?/g, '\n');
                if (!value) return false;
                output.push(inheritedPrefix + value);
                return true;
            }
            if (node.nodeType !== 1) return false;
            if (node.tagName === 'BR') {
                output.push('\n');
                return false;
            }
            const block = isBlock(node.tagName);
            if (block && output.length && !endsWithNewline()) output.push('\n');
            let prefix = inheritedPrefix + (node.dataset.mcPrefix || '');
            let emitted = false;
            node.childNodes.forEach(child => {
                if (walk(child, prefix)) {
                    prefix = '';
                    emitted = true;
                }
            });
            if (!emitted && node.dataset.mcPrefix && node.childNodes.length === 0) output.push(node.dataset.mcPrefix);
            if (block && output.length && !endsWithNewline()) output.push('\n');
            return emitted;
        };
        element.childNodes.forEach(child => walk(child));
        return output.join('').replace(/\r\n?/g, '\n').replace(/\n+$/, '');
    };

    const isRichSelectionInside = (element, range) => range
        && element.contains(range.startContainer)
        && element.contains(range.endContainer);

    const currentRichRange = element => {
        const selection = window.getSelection();
        if (!selection || selection.rangeCount === 0) return null;
        const range = selection.getRangeAt(0);
        return isRichSelectionInside(element, range) ? range.cloneRange() : null;
    };

    const rememberRichSelection = element => {
        const range = currentRichRange(element);
        if (!range) return;
        richSelections.set(element, {range, snapshot: richSelectionSource(element, range)});
    };

    const createEndRange = element => {
        const range = document.createRange();
        range.selectNodeContents(element);
        range.collapse(false);
        return range;
    };

    const visibleText = value => String(value).replace(/&([0-9a-fk-or])/gi, '');

    const visibleBoundary = (element, offset, preferEnd) => {
        const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
        let node;
        let lastTextNode = null;
        let remaining = Math.max(0, Number(offset) || 0);
        while ((node = walker.nextNode())) {
            lastTextNode = node;
            const length = node.nodeValue?.length || 0;
            if (remaining < length || (preferEnd && remaining === length)) return {container: node, offset: remaining};
            remaining -= length;
        }

        const trailingPrefix = [...element.children].reverse()
            .find(child => child.dataset.mcPrefix && !child.textContent);
        if (trailingPrefix) return {container: trailingPrefix, offset: trailingPrefix.childNodes.length};
        if (lastTextNode) return {container: lastTextNode, offset: lastTextNode.nodeValue.length};
        return {container: element, offset: element.childNodes.length};
    };

    const setVisibleSelection = (element, startOffset, endOffset = startOffset) => {
        const range = document.createRange();
        const start = visibleBoundary(element, startOffset, false);
        if (startOffset === endOffset) {
            range.setStart(start.container, start.offset);
            range.collapse(true);
        } else {
            const end = visibleBoundary(element, endOffset, true);
            range.setStart(start.container, start.offset);
            range.setEnd(end.container, end.offset);
        }
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        richSelections.set(element, {range: range.cloneRange(), snapshot: null});
    };

    const insertRichMarker = (range, marker, atEnd = false) => {
        const insertionRange = range.cloneRange();
        insertionRange.collapse(!atEnd);
        const node = document.createTextNode(marker);
        insertionRange.insertNode(node);
        return node;
    };

    const richSelectionSource = (element, range) => {
        const startMarker = '\uE000';
        const endMarker = '\uE001';
        let startNode;
        let endNode;
        if (range.collapsed) {
            startNode = insertRichMarker(range, startMarker);
        } else {
            endNode = insertRichMarker(range, endMarker, true);
            startNode = insertRichMarker(range, startMarker);
        }

        try {
            const normalized = normalizeMinecraftText(serializeRichEditor(element));
            const startIndex = normalized.indexOf(startMarker);
            const endIndex = normalized.indexOf(endMarker);
            const withoutMarkers = value => value.split(startMarker).join('').split(endMarker).join('');
            const rawIndexBefore = index => withoutMarkers(normalized.slice(0, index)).length;
            const visibleIndexBefore = index => visibleText(withoutMarkers(normalized.slice(0, index))).length;
            const source = withoutMarkers(normalized);
            const startRaw = startIndex >= 0 ? rawIndexBefore(startIndex) : source.length;
            const endRaw = endIndex >= 0 ? rawIndexBefore(endIndex) : startRaw;
            const startVisible = startIndex >= 0 ? visibleIndexBefore(startIndex) : visibleText(source).length;
            const endVisible = endIndex >= 0 ? visibleIndexBefore(endIndex) : startVisible;
            return {
                source,
                startRaw,
                endRaw,
                startVisible,
                endVisible,
                collapsed: range.collapsed
            };
        } finally {
            const parents = new Set([startNode?.parentNode, endNode?.parentNode].filter(Boolean));
            startNode?.remove();
            endNode?.remove();
            parents.forEach(parent => parent.normalize());
        }
    };

    const minecraftFormatState = source => {
        let state = minecraftStyle('f');
        for (const match of String(source).matchAll(/&([0-9a-fk-or])/gi)) {
            const code = match[1].toLowerCase();
            if (isMinecraftColor(code)) state = minecraftStyle(code);
            else if (code === 'r') state = minecraftStyle('f');
            else if (code === 'l') state.bold = true;
            else if (code === 'k') state.obfuscated = true;
            else if (code === 'm') state.strike = true;
            else if (code === 'n') state.underline = true;
            else if (code === 'o') state.italic = true;
        }
        return state;
    };

    const minecraftStyleCodes = state => [
        state.bold ? '&l' : '',
        state.italic ? '&o' : '',
        state.underline ? '&n' : '',
        state.strike ? '&m' : '',
        state.obfuscated ? '&k' : ''
    ].join('');

    const minecraftFormatCodes = state => `&${state.color}${minecraftStyleCodes(state)}`;

    const insertRichCode = (element, code) => {
        const saved = richSelections.get(element);
        const activeRange = currentRichRange(element);
        const range = activeRange || (saved && isRichSelectionInside(element, saved.range)
            ? saved.range.cloneRange() : createEndRange(element));
        const selection = activeRange
            ? richSelectionSource(element, activeRange)
            : saved?.snapshot || richSelectionSource(element, range);
        const isColorCode = /^&(?:[0-9a-f]|r)$/i.test(code);
        const before = selection.source.slice(0, selection.startRaw);
        const selected = selection.source.slice(selection.startRaw, selection.endRaw);
        const after = selection.source.slice(selection.endRaw);
        const selectedWithoutColors = isColorCode ? selected.replace(/&[0-9a-fr]/gi, '') : selected;
        const preserveStyles = isColorCode && code.toLowerCase() !== '&r'
            ? minecraftStyleCodes(minecraftFormatState(selection.source.slice(0, selection.startRaw))) : '';
        const restoreFormatting = isColorCode && code.toLowerCase() !== '&r'
        && visibleText(after).length > 0 && !/^&(?:[0-9a-f]|r)/i.test(after)
            ? minecraftFormatCodes(minecraftFormatState(selection.source.slice(0, selection.endRaw))) : '';
        const insertedCode = selection.collapsed && isColorCode ? `${code}${preserveStyles}` : code;
        const nextSource = selection.collapsed
            ? `${before}${insertedCode}${after}`
            : `${before}${code}${preserveStyles}${selectedWithoutColors}${restoreFormatting}${after}`;
        const normalizedNextSource = normalizeMinecraftText(nextSource);
        const beforeSnapshot = richHistorySnapshot(selection.source, selection.startVisible, selection.endVisible, selection.collapsed);
        const afterSnapshot = richHistorySnapshot(normalizedNextSource, selection.startVisible,
            selection.collapsed ? selection.startVisible : selection.endVisible, selection.collapsed);
        commitRichHistory(element, beforeSnapshot, afterSnapshot);
        renderRichEditor(element, normalizedNextSource);
        const restoreSelection = () => {
            if (!document.contains(element)) return;
            element.focus({preventScroll: true});
            setVisibleSelection(element, selection.startVisible, selection.collapsed ? selection.startVisible : selection.endVisible);
        };
        restoreSelection();
        window.setTimeout(restoreSelection, 0);
        window.requestAnimationFrame(restoreSelection);
    };

    const richHistorySnapshot = (source, startVisible, endVisible = startVisible, collapsed = startVisible === endVisible) => ({
        source: normalizeMinecraftText(source),
        startVisible: Math.max(0, Number(startVisible) || 0),
        endVisible: Math.max(0, Number(endVisible) || 0),
        collapsed
    });

    const readRichHistorySnapshot = element => {
        const range = currentRichRange(element);
        if (range) {
            const selection = richSelectionSource(element, range);
            return richHistorySnapshot(selection.source, selection.startVisible, selection.endVisible, selection.collapsed);
        }
        const source = normalizeMinecraftText(serializeRichEditor(element));
        const offset = visibleText(source).length;
        return richHistorySnapshot(source, offset);
    };

    const resetRichHistory = (element, source) => {
        const normalized = normalizeMinecraftText(source || '');
        const offset = visibleText(normalized).length;
        richSelections.delete(element);
        richHistories.set(element, {
            undo: [],
            redo: [],
            current: richHistorySnapshot(normalized, offset),
            pendingInput: null
        });
    };

    const ensureRichHistory = element => {
        let history = richHistories.get(element);
        if (!history) {
            history = {undo: [], redo: [], current: readRichHistorySnapshot(element), pendingInput: null};
            richHistories.set(element, history);
        }
        return history;
    };

    const flushRichInputHistory = element => {
        const history = richHistories.get(element);
        const pending = history?.pendingInput;
        if (!pending) return;
        if (pending.timer) window.clearTimeout(pending.timer);
        history.pendingInput = null;
        if (pending.before.source === history.current.source) return;
        history.undo.push(pending.before);
        if (history.undo.length > richHistoryLimit) history.undo.shift();
    };

    const commitRichHistory = (element, before, after) => {
        flushRichInputHistory(element);
        const history = ensureRichHistory(element);
        if (before.source === after.source) {
            history.current = after;
            return false;
        }
        if (history.current.source !== before.source) history.current = before;
        history.undo.push(before);
        if (history.undo.length > richHistoryLimit) history.undo.shift();
        history.current = after;
        history.redo = [];
        return true;
    };

    const recordRichInput = element => {
        const history = ensureRichHistory(element);
        const next = readRichHistorySnapshot(element);
        if (next.source === history.current.source) {
            history.current = next;
            return;
        }
        if (!history.pendingInput) {
            const pending = {before: history.current, timer: 0};
            history.pendingInput = pending;
            history.redo = [];
            pending.timer = window.setTimeout(() => {
                const current = richHistories.get(element);
                if (current?.pendingInput === pending) flushRichInputHistory(element);
            }, richInputGroupDelay);
        }
        history.current = next;
    };

    const applyRichHistorySnapshot = (element, snapshot) => {
        renderRichEditor(element, snapshot.source);
        const restoreSelection = () => {
            if (!document.contains(element)) return;
            element.focus({preventScroll: true});
            setVisibleSelection(element, snapshot.startVisible, snapshot.collapsed ? snapshot.startVisible : snapshot.endVisible);
        };
        restoreSelection();
        window.setTimeout(restoreSelection, 0);
        window.requestAnimationFrame(restoreSelection);
    };

    const undoRichEditor = element => {
        flushRichInputHistory(element);
        const history = ensureRichHistory(element);
        if (history.undo.length === 0) return false;
        const previous = history.undo.pop();
        history.redo.push(history.current);
        history.current = previous;
        applyRichHistorySnapshot(element, previous);
        return true;
    };

    const redoRichEditor = element => {
        flushRichInputHistory(element);
        const history = ensureRichHistory(element);
        if (history.redo.length === 0) return false;
        const next = history.redo.pop();
        history.undo.push(history.current);
        history.current = next;
        applyRichHistorySnapshot(element, next);
        return true;
    };

    const handleRichHistoryKeydown = (element, event) => {
        if (!(event.ctrlKey || event.metaKey)) return;
        const key = String(event.key || '').toLowerCase();
        const changed = key === 'z'
            ? event.shiftKey ? redoRichEditor(element) : undoRichEditor(element)
            : key === 'y' ? redoRichEditor(element) : false;
        if (changed) event.preventDefault();
    };

    const contentValue = id => {
        const element = $(id);
        if (element.classList.contains('rich-editor')) return normalizeMinecraftText(serializeRichEditor(element));
        const value = element.innerText ?? element.textContent ?? '';
        return value.replace(/\u00a0/g, ' ').replace(/\r\n?/g, '\n');
    };

    const setContentValue = (id, value) => {
        const element = $(id);
        if (element.classList.contains('rich-editor')) {
            const text = value || '';
            renderRichEditor(element, text);
            resetRichHistory(element, text);
        } else element.textContent = value || '';
    };

    const objectKey = (object, keys) => {
        if (!object || typeof object !== 'object' || Array.isArray(object)) return '';
        const wanted = new Set(keys.map(key => String(key).toLowerCase()));
        return Object.keys(object).find(key => wanted.has(key.toLowerCase())) || '';
    };

    const objectValue = (object, keys) => {
        const key = objectKey(object, keys);
        return key ? object[key] : undefined;
    };

    const objectMap = value => value && typeof value === 'object' && !Array.isArray(value) ? value : null;

    const textValue = (object, keys) => {
        const value = objectValue(object, keys);
        return value === undefined || value === null || String(value).trim() === '' ? '' : String(value);
    };

    const numberValue = (object, keys, fallback = null) => {
        const value = objectValue(object, keys);
        if (value === undefined || value === null || value === '') return fallback;
        const number = Number(value);
        return Number.isFinite(number) ? number : fallback;
    };

    const textLines = value => {
        if (Array.isArray(value)) return value.map(String);
        if (value === undefined || value === null) return [];
        return [String(value)];
    };

    const legacyItemStackView = configuration => {
        const stack = objectMap(objectValue(configuration, ['ItemStack']));
        if (!stack) return null;
        return {
            stack,
            meta: objectMap(objectValue(stack, ['meta', 'Meta'])) || {}
        };
    };

    const editorConfigurationView = (configuration, summary, preview = {}) => {
        const legacy = legacyItemStackView(configuration);
        const stack = legacy?.stack || {};
        const meta = legacy?.meta || {};
        const configuredDisplay = textValue(configuration, ['Display', 'display', 'Name', 'name']);
        const configuredLore = objectValue(configuration, ['Lore', 'lore']);
        const configuredEnchantments = objectValue(configuration, ['Enchantments', 'enchants']);
        const configuredFlags = objectValue(configuration, ['Hide', 'ItemFlags', 'itemFlags']);
        const configuredMaterial = textValue(configuration, ['Id', 'Material', 'Type']);
        const stackMaterial = textValue(stack, ['type', 'material', 'id']);
        const configuredData = numberValue(configuration, ['Data']);
        const configuredDurability = numberValue(configuration, ['Durability']);
        const stackDurability = numberValue(stack, ['damage', 'durability', 'data'], 0);
        const configuredAmount = numberValue(configuration, ['Amount']);
        const stackAmount = numberValue(stack, ['amount'], 1);
        const previewMaterial = textValue(preview, ['material']);
        const previewDisplay = textValue(preview, ['displayName']);
        const previewLore = objectValue(preview, ['lore']);
        const previewEnchantments = objectValue(preview, ['enchantments']);
        const previewFlags = objectValue(preview, ['flags']);
        const previewDurability = numberValue(preview, ['durability']);
        const previewAmount = numberValue(preview, ['amount']);
        const display = configuredDisplay || textValue(meta, ['display-name', 'displayName', 'name'])
            || previewDisplay || summary.displayName || summary.internalName;
        const lore = configuredLore !== undefined && configuredLore !== null
            ? textLines(configuredLore)
            : objectValue(meta, ['lore', 'Lore']) !== undefined
                ? textLines(objectValue(meta, ['lore', 'Lore'])) : textLines(previewLore);
        const enchantments = configuredEnchantments !== undefined && configuredEnchantments !== null
            ? configuredEnchantments
            : objectValue(meta, ['enchants', 'enchantments']) !== undefined
                ? objectValue(meta, ['enchants', 'enchantments']) : previewEnchantments;
        const flags = configuredFlags !== undefined && configuredFlags !== null
            ? configuredFlags
            : objectValue(meta, ['ItemFlags', 'itemFlags', 'item-flags']) !== undefined
                ? objectValue(meta, ['ItemFlags', 'itemFlags', 'item-flags']) : previewFlags;
        return {
            legacy,
            materialName: (configuredMaterial || stackMaterial || previewMaterial || summary.materialName || 'STONE').toUpperCase(),
            materialData: configuredData ?? configuredDurability ?? stackDurability ?? previewDurability ?? 0,
            durability: configuredDurability ?? stackDurability ?? previewDurability ?? 0,
            amount: Math.max(1, configuredAmount ?? stackAmount ?? previewAmount ?? summary.amount ?? 1),
            display,
            lore,
            enchantments,
            flags
        };
    };

    const setObjectField = (object, keys, value) => {
        const key = objectKey(object, keys) || keys[0];
        object[key] = value;
    };

    const removeObjectField = (object, keys) => {
        const key = objectKey(object, keys);
        if (key) delete object[key];
    };

    const legacyTextFormatter = () => {
        return value => normalizeMinecraftText(value).replace(/&([0-9a-fk-or])/gi, '§$1');
    };

    const updateLegacyItemStack = (configuration, display, lore) => {
        const legacy = legacyItemStackView(configuration);
        if (!legacy) return false;
        const {stack, meta} = legacy;
        setObjectField(stack, ['type', 'material', 'id'], state.materialName);
        const amount = Math.min(64, Math.max(1, Number($('item-amount').value) || 1));
        if (objectKey(stack, ['amount', 'Amount']) || amount !== 1) setObjectField(stack, ['amount', 'Amount'], amount);
        const data = Number($('material-data').value) || 0;
        const durability = Number($('item-durability').value) || 0;
        const damage = durability || data;
        const damageKey = objectKey(stack, ['damage', 'durability', 'data']);
        if (damageKey || data !== 0) {
            if (damage > 0) setObjectField(stack, ['damage', 'durability', 'data'], damage);
            else removeObjectField(stack, ['damage', 'durability', 'data']);
        }

        const formatText = legacyTextFormatter();
        if (display) setObjectField(meta, ['display-name', 'displayName', 'name'], formatText(display));
        else removeObjectField(meta, ['display-name', 'displayName', 'name']);
        if (lore.some(Boolean)) setObjectField(meta, ['lore', 'Lore'], lore.map(formatText));
        else removeObjectField(meta, ['lore', 'Lore']);

        const enchantments = Object.fromEntries([...state.enchantments].map(([key, level]) => [key, level]));
        if (state.enchantments.size) setObjectField(meta, ['enchants', 'enchantments'], enchantments);
        else removeObjectField(meta, ['enchants', 'enchantments']);
        if (state.flags.size) setObjectField(meta, ['ItemFlags', 'itemFlags', 'item-flags'], [...state.flags]);
        else removeObjectField(meta, ['ItemFlags', 'itemFlags', 'item-flags']);

        setObjectField(configuration, ['Id', 'Material', 'Type'], state.materialName);
        setObjectField(configuration, ['Data'], data);
        if (display) setObjectField(configuration, ['Display', 'display', 'Name', 'name'], display);
        else removeObjectField(configuration, ['Display', 'display', 'Name', 'name']);
        if (lore.some(Boolean)) setObjectField(configuration, ['Lore', 'lore'], lore);
        else removeObjectField(configuration, ['Lore', 'lore']);
        setObjectField(configuration, ['Amount'], amount);
        const normalizedDurability = Math.max(0, Number($('item-durability').value) || 0);
        if (normalizedDurability > 0) setObjectField(configuration, ['Durability'], normalizedDurability);
        else removeObjectField(configuration, ['Durability']);
        if (state.enchantments.size) setObjectField(configuration, ['Enchantments', 'enchants'], [...state.enchantments].map(([key, level]) => `${key}:${level}`));
        else removeObjectField(configuration, ['Enchantments', 'enchants']);
        if (state.flags.size) setObjectField(configuration, ['Hide', 'ItemFlags', 'itemFlags'], [...state.flags]);
        else removeObjectField(configuration, ['Hide', 'ItemFlags', 'itemFlags']);
        return true;
    };

    const selectedMaterial = () => {
        const entry = state.editorCatalog.materials.find(item => item.materialName === state.materialName && Number(item.data) === Number(state.materialData));
        return entry || state.editorCatalog.materials.find(item => item.materialName === state.materialName) || {
            materialName: state.materialName,
            data: state.materialData,
            displayName: state.materialName
        };
    };

    const renderMaterialCurrent = () => {
        const entry = selectedMaterial();
        $('material-current-name').textContent = entry.displayName || entry.materialName || state.materialName;
        setIcon($('material-current-icon'), iconCandidates(state.materialName, state.materialData), '?', state.materialName);
    };

    const renderMaterialOptions = () => {
        const query = $('material-search').value.trim().toLowerCase();
        const entries = state.editorCatalog.materials.filter(entry => {
            if (!query) return true;
            return String(entry.materialName).toLowerCase().includes(query) || String(entry.displayName).toLowerCase().includes(query);
        }).slice(0, 240);
        $('material-count').textContent = `${entries.length} 个材质`;
        const options = $('material-options');
        options.replaceChildren();
        for (const entry of entries) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = `material-option${entry.materialName === state.materialName && Number(entry.data) === Number(state.materialData) ? ' selected' : ''}`;
            const icon = document.createElement('span');
            icon.className = 'item-icon';
            setIcon(icon, iconCandidates(entry.materialName, entry.data), '?', entry.materialName);
            const copy = document.createElement('span');
            copy.className = 'material-option-copy';
            const strong = document.createElement('strong');
            strong.textContent = entry.displayName;
            const small = document.createElement('small');
            small.textContent = entry.materialName;
            copy.append(strong, small);
            const check = document.createElement('span');
            check.className = 'material-check';
            check.textContent = '✓';
            button.append(icon, copy, check);
            button.onclick = () => {
                state.materialName = entry.materialName;
                state.materialData = Number(entry.data);
                $('material-data').value = state.materialData;
                renderMaterialCurrent();
                renderMaterialOptions();
                closeMaterialMenu();
                renderPreview();
            };
            options.append(button);
        }
    };

    const closeMaterialMenu = () => {
        $('material-menu').classList.add('hidden');
        $('material-trigger').setAttribute('aria-expanded', 'false');
    };

    const renderFilters = () => {
        const tagSelect = $('tag-filter');
        const tagValue = tagSelect.value;
        tagSelect.replaceChildren(new Option('全部标签', ''));
        for (const tag of taxonomy().tags) tagSelect.append(new Option(tag.displayName, tag.id));
        tagSelect.value = tagValue;
    };

    const renderTags = selected => {
        const container = $('tag-options');
        container.replaceChildren();
        state.selectedTags = new Set(selected || state.selectedTags);
        for (const tag of taxonomy().tags) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'tag-check';
            button.setAttribute('aria-label', `选择标签 ${tag.displayName}`);
            const selectedTag = state.selectedTags.has(tag.id);
            button.classList.toggle('selected', selectedTag);
            button.setAttribute('aria-pressed', String(selectedTag));
            button.onclick = event => {
                event.preventDefault();
                event.stopPropagation();
                const nextSelected = !state.selectedTags.has(tag.id);
                if (nextSelected) state.selectedTags.add(tag.id);
                else state.selectedTags.delete(tag.id);
                button.classList.toggle('selected', nextSelected);
                button.setAttribute('aria-pressed', String(nextSelected));
                renderTagSummary();
            };
            const text = document.createElement('span');
            text.textContent = tag.displayName;
            applyTagVisual(button, tag.color);
            button.append(text);
            container.append(button);
        }
        renderTagSummary();
    };

    const renderTagSummary = () => {
        const map = tagMap();
        const names = [...state.selectedTags].map(id => map.get(id)?.displayName || id);
        $('tag-summary').value = names.length ? names.join('、') : '无标签';
    };

    const renderEnchantments = () => {
        const query = $('enchantment-search').value.trim().toLowerCase();
        const options = $('enchantment-options');
        options.replaceChildren();
        const entries = state.editorCatalog.enchantments.filter(entry => !query
            || entry.key.toLowerCase().includes(query)
            || enchantmentDisplayName(entry).toLowerCase().includes(query));
        for (const entry of entries) {
            const row = document.createElement('label');
            row.className = 'enchantment-option';
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = state.enchantments.has(entry.key);
            const copy = document.createElement('span');
            copy.className = 'enchantment-copy';
            const strong = document.createElement('strong');
            strong.textContent = enchantmentDisplayName(entry);
            const small = document.createElement('small');
            small.textContent = entry.key;
            copy.append(strong, small);
            const level = document.createElement('input');
            level.className = 'input enchantment-level';
            level.type = 'number';
            level.min = entry.startLevel;
            level.max = entry.maxLevel;
            level.value = state.enchantments.get(entry.key) || entry.startLevel;
            level.disabled = !checkbox.checked;
            checkbox.onchange = () => {
                if (checkbox.checked) state.enchantments.set(entry.key, Math.min(entry.maxLevel, Math.max(entry.startLevel, Number(level.value) || entry.startLevel)));
                else state.enchantments.delete(entry.key);
                level.disabled = !checkbox.checked;
                renderEnchantments();
                renderPreview();
            };
            level.oninput = () => {
                if (checkbox.checked) state.enchantments.set(entry.key, Math.min(entry.maxLevel, Math.max(entry.startLevel, Number(level.value) || entry.startLevel)));
                renderPreview();
            };
            row.append(checkbox, copy, level);
            options.append(row);
        }
        $('enchantment-count').textContent = `${state.enchantments.size} 项`;
    };

    const renderFlags = () => {
        const container = $('flag-options');
        container.replaceChildren();
        for (const flag of state.editorCatalog.itemFlags) {
            const label = document.createElement('label');
            label.className = 'hide-flag-option';
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = state.flags.has(flag);
            checkbox.onchange = () => {
                if (checkbox.checked) state.flags.add(flag);
                else state.flags.delete(flag);
                $('flag-count').textContent = `${state.flags.size} 项`;
                renderPreview();
            };
            const copy = document.createElement('span');
            copy.className = 'hide-flag-copy';
            const strong = document.createElement('strong');
            strong.textContent = itemFlagDisplayName(flag);
            const small = document.createElement('small');
            small.textContent = flag;
            copy.append(strong, small);
            label.append(checkbox, copy);
            container.append(label);
        }
        $('flag-count').textContent = `${state.flags.size} 项`;
    };

    const parseEnchantments = values => {
        const result = new Map();
        if (Array.isArray(values)) {
            for (const value of values) {
                const match = String(value).match(/^\s*([^:]+)\s*:\s*(\d+)\s*$/);
                if (match) result.set(match[1].trim().toUpperCase(), Number(match[2]));
            }
        } else if (values && typeof values === 'object') {
            for (const [key, value] of Object.entries(values)) result.set(key.toUpperCase(), Number(value) || 1);
        }
        return result;
    };

    const parseJsonField = (id, fallback) => {
        try {
            return JSON.parse($(id).value || JSON.stringify(fallback));
        } catch {
            throw new Error(`${id} 不是有效 JSON`);
        }
    };

    const updateAdvancedFields = config => {
        $('attributes-json').value = JSON.stringify(config.Attributes ?? {}, null, 2);
        $('options-json').value = JSON.stringify(config.Options ?? {}, null, 2);
        $('nbt-json').value = JSON.stringify(config.NBT ?? {}, null, 2);
        resizeConfiguration();
    };

    const fillEditor = details => {
        state.configuration = JSON.parse(JSON.stringify(details.configuration || {}));
        const config = state.configuration;
        const view = editorConfigurationView(config, details.summary, details.preview);
        state.materialName = view.materialName;
        state.materialData = Number(view.materialData) || 0;
        state.enchantments = parseEnchantments(view.enchantments);
        state.flags = new Set(Array.isArray(view.flags)
            ? view.flags.map(String)
            : view.flags == null ? [] : [String(view.flags)]);
        const classification = details.summary.classification || {tagIds: []};
        state.selectedTags = new Set(classification.tagIds || []);
        $('internal-name').value = details.summary.internalName;
        setContentValue('display-editor', view.display);
        setContentValue('lore-editor', view.lore.join('\n'));
        $('material-data').value = state.materialData;
        $('item-amount').value = view.amount;
        $('item-durability').value = view.durability;
        $('configuration').value = JSON.stringify(config, null, 2);
        $('raw-yaml').textContent = details.rawYaml || '';
        renderTags(classification.tagIds || []);
        renderMaterialCurrent();
        renderEnchantments();
        renderFlags();
        updateAdvancedFields(config);
        resizeConfiguration();
        renderPreview();
    };

    const renderPreview = () => {
        const display = contentValue('display-editor') || $('internal-name').value || '新物品';
        renderMinecraft($('preview-name'), display);
        const lore = contentValue('lore-editor').split('\n').filter(line => line.length > 0);
        const loreContainer = $('preview-lore');
        loreContainer.replaceChildren();
        for (const line of lore) {
            const row = document.createElement('div');
            appendMinecraftText(row, line);
            loreContainer.append(row);
        }
        setIcon($('preview-icon'), iconCandidates(state.materialName, state.materialData), 'MM', state.materialName);
    };

    const readEditorConfiguration = () => {
        const configuration = parseJsonField('configuration', {});
        if (!configuration || Array.isArray(configuration)) throw new Error('高级配置必须是对象');
        const display = contentValue('display-editor');
        const lore = contentValue('lore-editor').split('\n').map(line => line.replace(/\r/g, ''));
        const durability = Math.max(0, Number($('item-durability').value) || 0);
        const legacy = updateLegacyItemStack(configuration, display, lore);
        if (!legacy) {
            configuration.Amount = Math.min(64, Math.max(1, Number($('item-amount').value) || 1));
            configuration.Id = state.materialName;
            configuration.Data = Number($('material-data').value) || 0;
            configuration.Display = display;
            if (lore.some(Boolean)) configuration.Lore = lore;
            else delete configuration.Lore;
            if (durability > 0) configuration.Durability = durability;
            else delete configuration.Durability;
            configuration.Enchantments = [...state.enchantments].map(([key, level]) => `${key}:${level}`);
            if (state.enchantments.size === 0) delete configuration.Enchantments;
            configuration.Hide = [...state.flags];
            if (state.flags.size === 0) delete configuration.Hide;
        }
        const attributes = parseJsonField('attributes-json', {});
        if (!attributes || typeof attributes !== 'object') {
            throw new Error('Attributes 必须是 JSON 对象或数组');
        }
        if (attributes.length) configuration.Attributes = attributes;
        else if (!legacy) delete configuration.Attributes;
        const options = parseJsonField('options-json', {});
        if (!options || typeof options !== 'object' || Array.isArray(options)) {
            throw new Error('Options 必须是 JSON 对象');
        }
        if (Object.keys(options).length) configuration.Options = options;
        else if (!legacy) delete configuration.Options;
        const nbt = parseJsonField('nbt-json', {});
        if (!nbt || typeof nbt !== 'object' || Array.isArray(nbt)) {
            throw new Error('NBT 必须是 JSON 对象');
        }
        if (Object.keys(nbt).length) configuration.NBT = nbt;
        else if (!legacy) delete configuration.NBT;
        return configuration;
    };

    const classificationPayload = () => ({tagIds: [...state.selectedTags]});

    const selectableItems = items => items.filter(item => item.editable);

    const updateSelectionControls = items => {
        const selectable = selectableItems(items);
        const selectedOnPage = selectable.filter(item => state.selectedItems.has(item.internalName));
        const allSelected = selectable.length > 0 && selectedOnPage.length === selectable.length;
        const selectAll = $('select-all');
        selectAll.disabled = selectable.length === 0;
        selectAll.textContent = allSelected ? '取消全选' : '全选';
        selectAll.setAttribute('aria-pressed', String(allSelected));
        selectAll.setAttribute('aria-label', allSelected ? '取消选择本页物品' : '选择本页可删除物品');
        const selectedCount = state.selectedItems.size;
        $('bulk-delete').disabled = selectedCount === 0;
        $('bulk-delete').textContent = selectedCount > 0 ? `批量删除 (${selectedCount})` : '批量删除';
        $('selection-count').textContent = `已选 ${selectedCount} 项`;
    };

    const clearSelection = () => {
        state.selectedItems.clear();
        updateSelectionControls(state.visibleItems);
    };

    const renderRows = items => {
        const list = $('items');
        list.replaceChildren();
        state.visibleItems = items;
        $('empty').classList.toggle('hidden', items.length > 0);
        const tags = tagMap();
        for (const item of items) {
            if (item.editable && state.selectedItems.has(item.internalName)) state.selectedItems.set(item.internalName, item);
            if (!item.editable) state.selectedItems.delete(item.internalName);
            const row = document.createElement('div');
            const selected = state.selectedItems.has(item.internalName);
            row.className = `item-row${item.internalName === state.current ? ' active' : ''}${selected ? ' selected' : ''}`;
            row.setAttribute('role', 'button');
            row.setAttribute('tabindex', '0');
            row.setAttribute('aria-label', `编辑物品 ${item.internalName}`);
            row.setAttribute('aria-pressed', String(selected));
            row.onclick = () => openItem(item.internalName);
            row.onkeydown = event => {
                if (event.key !== 'Enter' && event.key !== ' ') return;
                event.preventDefault();
                void openItem(item.internalName);
            };
            row.innerHTML = '<div class="item-main"><input class="item-select" type="checkbox"><span class="item-icon missing"><span class="icon-fallback">MM</span></span><span class="item-copy"><span class="item-name"></span><span class="item-id"></span></span></div><span class="item-tags"></span><span class="item-binding"></span><div class="item-actions"></div>';
            const checkbox = row.querySelector('.item-select');
            checkbox.disabled = !item.editable;
            checkbox.checked = state.selectedItems.has(item.internalName);
            checkbox.setAttribute('aria-label', `选择物品 ${item.internalName}`);
            checkbox.onclick = event => event.stopPropagation();
            checkbox.onkeydown = event => event.stopPropagation();
            checkbox.onchange = event => {
                event.stopPropagation();
                if (checkbox.checked) state.selectedItems.set(item.internalName, item);
                else state.selectedItems.delete(item.internalName);
                row.classList.toggle('selected', checkbox.checked);
                row.setAttribute('aria-pressed', String(checkbox.checked));
                updateSelectionControls(state.visibleItems);
            };
            renderMinecraft(row.querySelector('.item-name'), item.displayName || item.internalName);
            row.querySelector('.item-id').textContent = item.internalName;
            const itemTags = row.querySelector('.item-tags');
            const tagEntries = (item.classification?.tagIds || []).map(id => tags.get(id) || {
                id,
                displayName: id,
                color: '#929394'
            });
            if (tagEntries.length === 0) tagEntries.push({displayName: '无标签', color: '#929394', untagged: true});
            for (const tagEntry of tagEntries) {
                const tag = document.createElement('span');
                tag.className = `tag item-tag${tagEntry.untagged ? ' untagged' : ''}`;
                tag.textContent = tagEntry.displayName;
                applyTagVisual(tag, tagEntry.color);
                itemTags.append(tag);
            }
            row.querySelector('.item-binding').textContent = item.materialName || '未知材质';
            setIcon(row.querySelector('.item-icon'), item.iconUrls || iconCandidates(item.materialName), 'MM', item.internalName);

            const actions = row.querySelector('.item-actions');
            const edit = document.createElement('button');
            edit.type = 'button';
            edit.className = 'button primary item-action';
            edit.textContent = '编辑';
            edit.disabled = !item.editable;
            edit.onclick = event => {
                event.stopPropagation();
                void openItem(item.internalName);
            };
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'button danger item-action';
            remove.textContent = '删除';
            remove.disabled = !item.editable;
            remove.onclick = event => {
                event.stopPropagation();
                void deleteItem(item);
            };
            actions.append(edit, remove);
            list.append(row);
        }
        updateSelectionControls(items);
    };

    const importFormatName = format => format === 'LEGACY_ITEMSTACK' ? '旧版 ItemStack' : 'MM 物品配置';

    const formatFileSize = bytes => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`;
        return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    };

    const importLine = (text, className = '') => {
        const line = document.createElement('div');
        line.className = `import-line${className ? ` ${className}` : ''}`;
        line.textContent = text;
        return line;
    };

    const renderImportSelection = () => {
        const list = $('import-file-list');
        list.replaceChildren();
        for (const file of state.importFiles) {
            const row = document.createElement('div');
            row.className = 'import-file-row';
            const name = document.createElement('strong');
            name.textContent = file.name;
            const size = document.createElement('span');
            size.textContent = formatFileSize(file.size);
            row.append(name, size);
            list.append(row);
        }
        $('import-summary').textContent = `已选择 ${state.importFiles.length} 个 YAML 文件，正在自动识别…`;
    };

    const renderImportResult = result => {
        const candidates = result.candidates || [];
        const conflicts = result.conflicts || [];
        const warnings = result.warnings || [];
        const errors = result.errors || [];
        const stateLine = $('import-state');
        stateLine.className = 'import-state';
        if (result.status === 'IMPORTED') stateLine.classList.add('success');
        if (result.status === 'CONFLICT' || result.status === 'INVALID' || result.status === 'FAILED') {
            stateLine.classList.add('error');
        }
        stateLine.textContent = result.message || '导入结果';
        $('import-summary').textContent = `${result.fileCount || state.importFiles.length} 个文件 · 已识别 ${candidates.length} 个物品`;
        const list = $('import-file-list');
        list.replaceChildren();
        for (const candidate of candidates) {
            const row = document.createElement('div');
            row.className = 'import-file-row';
            const name = document.createElement('strong');
            name.textContent = candidate.internalName;
            const source = document.createElement('span');
            source.textContent = `${candidate.fileName} · ${importFormatName(candidate.format)}`;
            row.append(name, source);
            list.append(row);
        }
        for (const conflict of conflicts) list.append(importLine(`冲突：${conflict}`, 'error'));
        for (const error of errors) list.append(importLine(`错误：${error}`, 'error'));
        for (const warning of warnings) list.append(importLine(`提示：${warning}`, 'warning'));
        $('import-confirm').disabled = state.importBusy
            || result.status !== 'PREVIEW'
            || candidates.length === 0
            || conflicts.length > 0
            || errors.length > 0;
    };

    const uploadImport = async path => {
        const form = new FormData();
        for (const file of state.importFiles) form.append('files', file, file.name);
        return request(path, {method: 'POST', body: form});
    };

    const previewImport = async files => {
        state.importFiles = files;
        state.importResult = null;
        state.importBusy = true;
        renderImportSelection();
        $('import-confirm').disabled = true;
        const dialog = $('import-dialog');
        if (!dialog.open) dialog.showModal();
        try {
            state.importResult = await uploadImport('/api/import/preview');
            renderImportResult(state.importResult);
        } catch (error) {
            $('import-state').className = 'import-state error';
            $('import-state').textContent = error.message || String(error);
            $('import-summary').textContent = '识别失败，未写入任何文件';
            $('import-file-list').replaceChildren();
            $('import-file-list').append(importLine('请检查文件格式后重试。', 'error'));
            $('import-confirm').disabled = true;
        } finally {
            state.importBusy = false;
            if (state.importResult) renderImportResult(state.importResult);
        }
    };

    const commitImport = async () => {
        if (state.importBusy || !state.importResult || state.importResult.status !== 'PREVIEW') return;
        state.importBusy = true;
        $('import-confirm').disabled = true;
        $('import-state').className = 'import-state';
        $('import-state').textContent = '正在写入 MM 物品库并重新加载…';
        try {
            const result = await uploadImport('/api/import');
            state.importResult = result;
            if (result.status === 'IMPORTED') {
                await loadEditorCatalog();
                await loadItems();
                showToast(result.message || 'MM 物品已导入');
                $('import-dialog').close();
            } else {
                renderImportResult(result);
            }
        } catch (error) {
            $('import-state').className = 'import-state error';
            $('import-state').textContent = error.message || String(error);
        } finally {
            state.importBusy = false;
            if ($('import-dialog').open && state.importResult?.status !== 'IMPORTED') {
                renderImportResult(state.importResult || {
                    status: 'FAILED',
                    message: '导入失败',
                    fileCount: 0,
                    candidates: [],
                    conflicts: [],
                    warnings: [],
                    errors: []
                });
            }
        }
    };

    const loadItems = async () => {
        const params = new URLSearchParams({
            page: String(state.page),
            pageSize: String(state.pageSize),
            search: document.getElementById('search')?.value || '',
            tag: document.getElementById('tag-filter')?.value || ''
        });
        const result = await request(`/api/items?${params}`);
        state.page = result.page;
        state.total = result.total;
        $('page-label').textContent = `第 ${result.page + 1} 页 · ${result.total} 项`;
        $('previous').disabled = result.page === 0;
        $('next').disabled = (result.page + 1) * result.pageSize >= result.total;
        renderRows(result.items || []);
    };

    const loadEditorCatalog = async () => {
        state.editorCatalog = await request('/api/editor/catalog');
        renderFilters();
        renderTags();
        renderMaterialOptions();
        renderEnchantments();
        renderFlags();
    };

    const openItem = async id => {
        try {
            const result = await request(`/api/items/${encodeURIComponent(id)}`);
            state.current = id;
            state.creating = false;
            state.revision = result.revision;
            $('editor-title').textContent = id;
            $('editor-source').textContent = result.summary.managed
                ? 'MM 物品文件 · 可编辑'
                : `外部 MM · ${result.summary.relativeFile} · 可编辑`;
            $('save').disabled = !result.summary.editable;
            $('delete').disabled = !result.summary.editable;
            $('save-top').disabled = !result.summary.editable;
            $('editor-empty').classList.add('hidden');
            $('editor-content').classList.remove('hidden');
            fillEditor(result);
            await loadItems();
        } catch (error) {
            showError(error);
        }
    };

    const newItem = () => {
        state.current = '';
        state.creating = true;
        state.revision = '';
        state.configuration = {Id: 'STONE', Display: '新物品', Amount: 1};
        state.materialName = 'STONE';
        state.materialData = 0;
        state.enchantments = new Map();
        state.flags = new Set();
        state.selectedTags = new Set();
        $('editor-title').textContent = '新建 MM 物品';
        $('editor-source').textContent = 'MM 物品文件 · 可编辑';
        $('editor-empty').classList.add('hidden');
        $('editor-content').classList.remove('hidden');
        $('internal-name').value = '';
        setContentValue('display-editor', '新物品');
        setContentValue('lore-editor', '');
        $('material-data').value = 0;
        $('item-amount').value = 1;
        $('item-durability').value = 0;
        $('configuration').value = JSON.stringify(state.configuration, null, 2);
        $('raw-yaml').textContent = '';
        $('save').disabled = false;
        $('delete').disabled = true;
        renderTags([]);
        renderMaterialCurrent();
        renderEnchantments();
        renderFlags();
        updateAdvancedFields(state.configuration);
        renderPreview();
    };

    const clearEditor = () => {
        richSelections.delete($('display-editor'));
        richSelections.delete($('lore-editor'));
        richHistories.delete($('display-editor'));
        richHistories.delete($('lore-editor'));
        state.current = '';
        state.creating = false;
        state.revision = '';
        $('editor-title').textContent = '编辑器';
        $('editor-source').textContent = '';
        $('preview-name').textContent = '请选择一个物品。';
        $('preview-lore').replaceChildren();
        setIcon($('preview-icon'), [], 'MM', '');
        $('save').disabled = true;
        $('delete').disabled = true;
        $('save-top').disabled = true;
        $('editor-empty').classList.remove('hidden');
        $('editor-content').classList.add('hidden');
    };

    const saveItem = async () => {
        const internalName = $('internal-name').value.trim();
        if (!internalName) {
            showError(new Error('内部名不能为空'));
            return;
        }
        if (!state.creating && internalName !== state.current
            && !window.confirm(`将 ${state.current} 改名为 ${internalName}？\nMM 引用会按精确物品 ID 迁移。`)) return;
        try {
            const configuration = readEditorConfiguration();
            const body = {
                internalName,
                newInternalName: internalName,
                configuration,
                classification: classificationPayload(),
                expectedRevision: state.revision,
                confirmExternalMutation: !state.creating
            };
            const result = await request(state.creating ? '/api/items' : `/api/items/${encodeURIComponent(state.current)}`, {
                method: state.creating ? 'POST' : 'PUT',
                body: JSON.stringify(body)
            });
            setMessage(result.message || '已保存');
            state.current = result.internalName || internalName;
            state.creating = false;
            await loadEditorCatalog();
            await loadItems();
            await openItem(state.current);
            setMessage('');
            showToast(result.message || '已保存');
        } catch (error) {
            showError(error);
        }
    };

    const deleteItem = async (item = null) => {
        const target = item || (state.current ? {internalName: state.current, revision: state.revision} : null);
        if (!target || !window.confirm(`删除 ${target.internalName}？\n外部 MM 文件也会被直接修改。`)) return;
        try {
            const result = await request(`/api/items/${encodeURIComponent(target.internalName)}`, {
                method: 'DELETE',
                body: JSON.stringify({expectedRevision: target.revision || '', confirmExternalMutation: true})
            });
            state.selectedItems.delete(target.internalName);
            if (target.internalName === state.current) clearEditor();
            await loadItems();
            setMessage('');
            showToast(result.message || '已删除');
        } catch (error) {
            showError(error);
        }
    };

    const bulkDeleteItems = async () => {
        const targets = [...state.selectedItems.values()].filter(item => item.editable);
        if (targets.length === 0) return;
        if (!window.confirm(`确定删除选中的 ${targets.length} 个物品？\n外部 MM 文件也会被直接修改，被 MM 配置引用的物品会跳过。`)) return;

        const successful = [];
        const failed = [];
        $('bulk-delete').disabled = true;
        for (const target of targets) {
            try {
                await request(`/api/items/${encodeURIComponent(target.internalName)}`, {
                    method: 'DELETE',
                    body: JSON.stringify({expectedRevision: target.revision || '', confirmExternalMutation: true})
                });
                successful.push(target.internalName);
                state.selectedItems.delete(target.internalName);
                if (target.internalName === state.current) clearEditor();
            } catch (error) {
                failed.push(`${target.internalName}: ${error.message || String(error)}`);
            }
        }

        try {
            await loadItems();
        } catch (error) {
            updateSelectionControls(state.visibleItems);
            showError(error);
            return;
        }
        if (failed.length > 0) {
            setMessage(`批量删除完成: 成功 ${successful.length} 项, 失败 ${failed.length} 项\n${failed.join('\n')}`, true);
            if (successful.length > 0) showToast(`已删除 ${successful.length} 项, ${failed.length} 项失败`);
            return;
        }
        setMessage('');
        showToast(`已删除 ${successful.length} 项`);
    };

    const toggleSelectAll = () => {
        const selectable = selectableItems(state.visibleItems);
        if (selectable.length === 0) return;
        const allSelected = selectable.every(item => state.selectedItems.has(item.internalName));
        for (const item of selectable) {
            if (allSelected) state.selectedItems.delete(item.internalName);
            else state.selectedItems.set(item.internalName, item);
        }
        renderRows(state.visibleItems);
    };

    const bindRichToolbars = () => {
        document.querySelectorAll('[data-toolbar]').forEach(toolbar => {
            const target = $(toolbar.dataset.toolbar);
            if (!target) return;
            ['focus', 'input', 'keyup', 'mouseup'].forEach(eventName => {
                target.addEventListener(eventName, () => {
                    rememberRichSelection(target);
                    if (eventName === 'input') recordRichInput(target);
                });
            });
            target.addEventListener('keydown', event => handleRichHistoryKeydown(target, event));
            toolbar.querySelectorAll('[data-code]').forEach(button => {
                button.type = 'button';
                button.onmousedown = event => {
                    rememberRichSelection(target);
                    event.preventDefault();
                };
                button.onclick = event => {
                    event.preventDefault();
                    event.stopPropagation();
                    insertRichCode(target, button.dataset.code);
                    renderPreview();
                };
            });
        });
    };

    const buildTaxonomyColorToolbar = () => {
        const toolbar = $('taxonomy-color-toolbar');
        toolbar.replaceChildren();
        const seen = new Set();
        for (const source of document.querySelectorAll('.rich-color[data-code]')) {
            const code = source.dataset.code;
            if (seen.has(code) || !minecraftColorValues[code]) continue;
            seen.add(code);
            const button = source.cloneNode(true);
            button.type = 'button';
            button.classList.add('tag-color-choice');
            button.style.setProperty('--tag-color', readableTagColor(minecraftColorValues[code]));
            button.onclick = event => {
                event.preventDefault();
                state.taxonomyColor = minecraftColorValues[code];
                renderTaxonomyColor();
            };
            toolbar.append(button);
        }
        renderTaxonomyColor();
    };

    const renderTaxonomyColor = () => {
        const selectedColor = normalizeHexColor(state.taxonomyColor);
        document.querySelectorAll('#taxonomy-color-toolbar .tag-color-choice').forEach(button => {
            const color = normalizeHexColor(minecraftColorValues[button.dataset.code]);
            button.style.setProperty('--tag-color', readableTagColor(color));
            button.classList.toggle('selected', color === selectedColor);
        });
        state.taxonomyColor = selectedColor;
        renderTaxonomyPreview();
    };

    const renderTaxonomyPreview = () => {
        const preview = $('taxonomy-color-preview');
        preview.textContent = $('taxonomy-display').value.trim() || '标签预览';
        applyTagVisual(preview, state.taxonomyColor);
    };

    const renderTaxonomyLists = () => {
        const entries = taxonomy().tags;
        const pageCount = Math.max(1, Math.ceil(entries.length / state.taxonomyPageSize));
        state.taxonomyPage = Math.min(state.taxonomyPage, pageCount - 1);
        const start = state.taxonomyPage * state.taxonomyPageSize;
        const tags = $('tag-list');
        tags.replaceChildren();
        for (const entry of entries.slice(start, start + state.taxonomyPageSize)) tags.append(taxonomyRow(entry));
        $('tag-empty').classList.toggle('hidden', entries.length > 0);
        tags.classList.toggle('hidden', entries.length === 0);
        $('tag-pager').classList.toggle('hidden', entries.length <= state.taxonomyPageSize);
        $('tag-page-label').textContent = entries.length ? `第 ${state.taxonomyPage + 1} / ${pageCount} 页` : '暂无标签';
        $('tag-previous').disabled = state.taxonomyPage === 0;
        $('tag-next').disabled = state.taxonomyPage >= pageCount - 1;
    };

    const taxonomyRow = entry => {
        const row = document.createElement('div');
        row.className = 'tag-row';
        const name = document.createElement('span');
        name.className = 'tag-row-name';
        const chip = document.createElement('span');
        chip.className = 'tag item-tag';
        chip.textContent = entry.displayName;
        applyTagVisual(chip, entry.color);
        name.append(chip);
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'button secondary';
        edit.textContent = '编辑';
        edit.onclick = () => openTaxonomyEditor(entry);
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'button danger';
        remove.textContent = '删除';
        remove.onclick = async () => {
            if (!window.confirm(`删除标签 ${entry.displayName}？`)) return;
            try {
                await request(`/api/taxonomy/tags/${encodeURIComponent(entry.id)}`, {method: 'DELETE'});
                await loadEditorCatalog();
                renderTaxonomyLists();
                showToast('标签已删除');
            } catch (error) {
                showError(error);
            }
        };
        row.append(name, edit, remove);
        return row;
    };

    const openTaxonomyEditor = (entry = null) => {
        state.taxonomyEditor = {oldId: entry?.id || ''};
        $('taxonomy-display').value = entry?.displayName || '';
        state.taxonomyColor = entry?.color || '#929394';
        renderTaxonomyColor();
        $('taxonomy-list-section').classList.add('hidden');
        $('taxonomy-editor').classList.remove('hidden');
        $('taxonomy-display').focus();
    };

    const closeTaxonomyEditor = () => {
        state.taxonomyEditor = null;
        $('taxonomy-list-section').classList.remove('hidden');
        $('taxonomy-editor').classList.add('hidden');
    };

    const saveTaxonomy = async () => {
        const editor = state.taxonomyEditor;
        if (!editor) return;
        const displayName = $('taxonomy-display').value.trim();
        if (!displayName) {
            showError(new Error('标签显示名称不能为空'));
            return;
        }
        try {
            await request(editor.oldId ? `/api/taxonomy/tags/${encodeURIComponent(editor.oldId)}` : '/api/taxonomy/tags', {
                method: editor.oldId ? 'PUT' : 'POST',
                body: JSON.stringify({displayName, color: state.taxonomyColor})
            });
            closeTaxonomyEditor();
            await loadEditorCatalog();
            renderTaxonomyLists();
            showToast(editor.oldId ? '标签已更新' : '标签已创建');
        } catch (error) {
            showError(error);
        }
    };

    const authenticate = async () => {
        const candidate = window.prompt('请输入 MythicMobsAddon Web Token');
        if (!candidate || !candidate.trim()) return;
        state.authToken = candidate.trim();
        try {
            await request('/api/status');
            document.body.classList.remove('locked');
            document.body.classList.add('unlocked');
            $('app-shell').setAttribute('aria-hidden', 'false');
            $('status-line').textContent = '已连接';
            $('status-line').classList.remove('offline');
            $('status-line').classList.add('online');
            await loadEditorCatalog();
            await loadItems();
            clearEditor();
        } catch {
            state.authToken = '';
            document.body.className = 'locked';
            $('app-shell').setAttribute('aria-hidden', 'true');
        }
    };

    $('reload').onclick = async () => {
        try {
            await request('/api/reload', {method: 'POST', body: '{}'});
            await loadEditorCatalog();
            await loadItems();
            if (state.current && !state.creating) await openItem(state.current);
            showToast('已刷新');
        } catch (error) {
            showError(error);
        }
    };
    $('save-top').onclick = saveItem;
    $('new-item').onclick = newItem;
    $('select-all').onclick = toggleSelectAll;
    $('bulk-delete').onclick = bulkDeleteItems;
    $('import-items').onclick = () => $('import-files').click();
    $('import-files').onchange = () => {
        const files = [...($('import-files').files || [])];
        $('import-files').value = '';
        if (files.length) void previewImport(files);
    };
    $('import-confirm').onclick = commitImport;
    $('import-dialog').addEventListener('close', () => {
        state.importFiles = [];
        state.importResult = null;
        state.importBusy = false;
    });
    $('save').onclick = saveItem;
    $('delete').onclick = deleteItem;
    $('manage-taxonomy').onclick = () => {
        closeTaxonomyEditor();
        state.taxonomyPage = 0;
        renderTaxonomyLists();
        $('taxonomy-dialog').showModal();
    };
    $('taxonomy-dialog').addEventListener('close', closeTaxonomyEditor);
    $('new-tag').onclick = () => openTaxonomyEditor();
    $('taxonomy-save').onclick = saveTaxonomy;
    $('taxonomy-cancel').onclick = closeTaxonomyEditor;
    $('taxonomy-display').oninput = renderTaxonomyPreview;
    $('tag-previous').onclick = () => {
        state.taxonomyPage = Math.max(0, state.taxonomyPage - 1);
        renderTaxonomyLists();
    };
    $('tag-next').onclick = () => {
        state.taxonomyPage += 1;
        renderTaxonomyLists();
    };
    $('material-trigger').onclick = () => {
        const menu = $('material-menu');
        menu.classList.toggle('hidden');
        $('material-trigger').setAttribute('aria-expanded', String(!menu.classList.contains('hidden')));
        if (!menu.classList.contains('hidden')) {
            $('material-search').focus();
            renderMaterialOptions();
        }
    };
    $('material-search').oninput = renderMaterialOptions;
    $('material-data').oninput = event => {
        state.materialData = Math.max(0, Number(event.target.value) || 0);
        renderMaterialCurrent();
        renderPreview();
    };
    $('enchantment-search').oninput = renderEnchantments;
    $('tag-filter').onchange = () => {
        clearSelection();
        state.page = 0;
        loadItems().catch(showError);
    };
    $('search').oninput = () => {
        clearSelection();
        state.page = 0;
        loadItems().catch(showError);
    };
    $('previous').onclick = () => {
        state.page = Math.max(0, state.page - 1);
        loadItems().catch(showError);
    };
    $('next').onclick = () => {
        state.page += 1;
        loadItems().catch(showError);
    };
    $('display-editor').oninput = renderPreview;
    $('lore-editor').oninput = renderPreview;
    $('configuration').oninput = resizeConfiguration;
    ['attributes-json', 'options-json', 'nbt-json'].forEach(id => $(id).oninput = resizeConfiguration);
    $('internal-name').oninput = renderPreview;
    window.addEventListener('resize', resizeConfiguration);
    document.addEventListener('click', event => {
        if (!$('material-menu').contains(event.target) && !$('material-trigger').contains(event.target)) closeMaterialMenu();
    });
    document.addEventListener('selectionchange', () => {
        const activeElement = document.activeElement;
        if (activeElement?.classList.contains('rich-editor')) rememberRichSelection(activeElement);
    });
    bindRichToolbars();
    buildTaxonomyColorToolbar();
    resizeConfiguration();
    window.setTimeout(authenticate, 0);
})();
