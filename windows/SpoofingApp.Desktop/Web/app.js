'use strict';

// Present only inside the WebView2 host. Without it the page still runs, so the UI can be
// opened in a plain browser for design work.
const bridge = window.chrome && window.chrome.webview ? window.chrome.webview : null;

const state = {
    serial: null,
    isEmulator: false,
    mode: 'fixed',
    target: null,
    waypoints: [],
    speedKmh: 18,
    loop: false,
    accuracy: 5,
    running: false,
    spoofed: null,
};

const el = (id) => document.getElementById(id);

/* ---------------------------------------------------------------- map --- */

const map = L.map('map', { worldCopyJump: true }).setView([20, 0], 3);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors',
}).addTo(map);

const layers = L.layerGroup().addTo(map);

map.on('click', (event) => {
    addPoint({ lat: event.latlng.lat, lng: wrapLongitude(event.latlng.lng) });
});

function wrapLongitude(lng) {
    return ((((lng + 180) % 360) + 360) % 360) - 180;
}

function addPoint(point) {
    if (state.mode === 'fixed') {
        state.target = point;
    } else {
        state.waypoints.push(point);
    }
    render();
}

function render() {
    layers.clearLayers();

    if (state.mode === 'route') {
        if (state.waypoints.length >= 2) {
            const line = state.waypoints.map((p) => [p.lat, p.lng]);
            if (state.loop) line.push([state.waypoints[0].lat, state.waypoints[0].lng]);
            L.polyline(line, { color: '#f59e0b', weight: 4, opacity: 0.85 }).addTo(layers);
        }
        state.waypoints.forEach((point, index) => {
            L.marker([point.lat, point.lng], {
                icon: L.divIcon({
                    className: '',
                    html: `<div class="waypoint-pin">${index + 1}</div>`,
                    iconSize: [22, 22],
                    iconAnchor: [11, 11],
                }),
            }).addTo(layers);
        });
    } else if (state.target) {
        L.circleMarker([state.target.lat, state.target.lng], {
            radius: 9, color: '#ffffff', weight: 2, fillColor: '#ef4444', fillOpacity: 1,
        }).addTo(layers);
    }

    if (state.spoofed) {
        L.circleMarker([state.spoofed.lat, state.spoofed.lng], {
            radius: 7, color: '#ffffff', weight: 2, fillColor: '#4f8cff', fillOpacity: 1,
        }).addTo(layers);
    }

    renderPanel();
}

/* --------------------------------------------------------------- panel --- */

function renderPanel() {
    const isRoute = state.mode === 'route';
    el('route-controls').classList.toggle('hidden', !isRoute);
    el('mode-fixed').classList.toggle('active', !isRoute);
    el('mode-route').classList.toggle('active', isRoute);

    el('target').textContent = isRoute
        ? (state.waypoints.length ? `Last: ${formatPoint(state.waypoints[state.waypoints.length - 1])}` : 'Click the map to add waypoints')
        : (state.target ? formatPoint(state.target) : 'Click the map to choose a location');

    el('route-summary').textContent = state.waypoints.length
        ? `${state.waypoints.length} waypoints · ${formatDistance(routeDistance())}`
        : 'No waypoints yet';
    el('undo').disabled = state.waypoints.length === 0;
    el('clear').disabled = state.waypoints.length === 0;

    el('start').disabled = !canStart();
    el('start').textContent = state.running ? 'Update' : 'Start spoofing';
    el('stop').classList.toggle('hidden', !state.running);

    el('speed-value').textContent = `${state.speedKmh} km/h`;
    el('accuracy-value').textContent = `${state.accuracy} m`;
    document.querySelectorAll('.chip-button').forEach((button) => {
        button.classList.toggle('active', Number(button.dataset.speed) === state.speedKmh);
    });

    el('status').textContent = state.running
        ? (state.spoofed ? `Spoofing ${formatPoint(state.spoofed)}` : 'Spoofing')
        : 'Not spoofing';
}

function canStart() {
    if (!state.serial) return false;
    return state.mode === 'fixed' ? Boolean(state.target) : state.waypoints.length >= 2;
}

function formatPoint(point) {
    return `${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}`;
}

function formatDistance(meters) {
    return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(2)} km`;
}

function distanceMeters(from, to) {
    const radians = (deg) => (deg * Math.PI) / 180;
    const lat1 = radians(from.lat);
    const lat2 = radians(to.lat);
    const dLat = lat2 - lat1;
    const dLon = radians(to.lng - from.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
    return 2 * 6371008.8 * Math.asin(Math.min(1, Math.sqrt(h)));
}

function routeDistance() {
    const points = state.waypoints.slice();
    if (state.loop && points.length > 1) points.push(points[0]);
    let total = 0;
    for (let i = 0; i < points.length - 1; i++) total += distanceMeters(points[i], points[i + 1]);
    return total;
}

function log(line) {
    const pre = el('log');
    pre.textContent += `[${new Date().toLocaleTimeString()}] ${line}\n`;
    const lines = pre.textContent.split('\n');
    if (lines.length > 200) pre.textContent = lines.slice(-200).join('\n');
    pre.scrollTop = pre.scrollHeight;
}

/* -------------------------------------------------------------- bridge --- */

function post(message) {
    if (bridge) {
        bridge.postMessage(message);
    } else {
        log(`preview: ${JSON.stringify(message)}`);
    }
}

function handle(message) {
    switch (message.type) {
        case 'adb':
            el('adb-path').textContent = message.found
                ? `adb: ${message.path}`
                : 'adb not found. Install the Android SDK platform-tools.';
            break;
        case 'devices':
            setDevices(message.devices || []);
            break;
        case 'status':
            setDeviceStatus(message);
            break;
        case 'running':
            state.running = message.running;
            if (!message.running) state.spoofed = null;
            render();
            break;
        case 'position':
            state.spoofed = { lat: message.lat, lng: message.lng };
            render();
            break;
        case 'log':
            log(message.line);
            break;
        default:
            break;
    }
}

function setDevices(devices) {
    const select = el('device');
    select.innerHTML = '';

    if (devices.length === 0) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'No devices. Connect one with USB debugging.';
        select.appendChild(option);
        state.serial = null;
        setDeviceStatus({ unknown: true });
        render();
        return;
    }

    devices.forEach((device) => {
        const option = document.createElement('option');
        option.value = device.serial;
        option.textContent = device.ready ? device.label : `${device.label} (unauthorized)`;
        option.dataset.emulator = device.isEmulator ? '1' : '';
        select.appendChild(option);
    });

    const chosen = devices.find((d) => d.serial === state.serial) || devices[0];
    state.serial = chosen.serial;
    state.isEmulator = chosen.isEmulator;
    select.value = chosen.serial;
    post({ type: 'status', serial: state.serial });
    render();
}

function setDeviceStatus(message) {
    const app = el('chip-app');
    const mock = el('chip-mock');
    if (message.unknown) {
        app.textContent = 'app: unknown';
        mock.textContent = 'mock: unknown';
        app.className = 'chip';
        mock.className = 'chip';
        return;
    }
    app.textContent = message.appInstalled ? 'app installed' : 'app missing';
    app.className = `chip ${message.appInstalled ? 'ok' : 'bad'}`;
    mock.textContent = message.mockAllowed ? 'mock allowed' : 'mock not allowed';
    mock.className = `chip ${message.mockAllowed ? 'ok' : 'bad'}`;
}

/* -------------------------------------------------------------- search --- */

function parseCoordinates(text) {
    const match = text.trim().match(/^\(?\s*([-+]?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*([-+]?\d{1,3}(?:\.\d+)?)\s*\)?$/);
    if (!match) return null;
    const lat = parseFloat(match[1]);
    const lng = parseFloat(match[2]);
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
    return { lat, lng };
}

async function search() {
    const query = el('search').value.trim();
    if (!query) return;

    const coordinates = parseCoordinates(query);
    if (coordinates) {
        hideResults();
        choose(coordinates);
        return;
    }

    try {
        const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=${encodeURIComponent(query)}`;
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const places = await response.json();
        showResults(places.map((place) => ({
            label: place.display_name,
            lat: parseFloat(place.lat),
            lng: parseFloat(place.lon),
        })));
    } catch (error) {
        hideResults();
        log(`Search failed: ${error.message}`);
    }
}

function showResults(places) {
    const list = el('results');
    list.innerHTML = '';
    if (places.length === 0) {
        log('No places found.');
        list.classList.add('hidden');
        return;
    }
    places.forEach((place) => {
        const item = document.createElement('li');
        item.textContent = place.label;
        const coords = document.createElement('small');
        coords.textContent = formatPoint(place);
        item.appendChild(coords);
        item.addEventListener('click', () => {
            hideResults();
            choose(place);
        });
        list.appendChild(item);
    });
    list.classList.remove('hidden');
}

function hideResults() {
    el('results').classList.add('hidden');
}

function choose(point) {
    addPoint({ lat: point.lat, lng: point.lng });
    map.setView([point.lat, point.lng], Math.max(map.getZoom(), 15));
}

/* -------------------------------------------------------------- events --- */

el('device').addEventListener('change', (event) => {
    state.serial = event.target.value || null;
    state.isEmulator = event.target.selectedOptions[0]?.dataset.emulator === '1';
    if (state.serial) post({ type: 'status', serial: state.serial });
    render();
});
el('refresh').addEventListener('click', () => post({ type: 'refresh' }));
el('prepare').addEventListener('click', () => post({ type: 'prepare', serial: state.serial }));
el('install').addEventListener('click', () => post({ type: 'install', serial: state.serial }));

el('mode-fixed').addEventListener('click', () => {
    state.mode = 'fixed';
    render();
});
el('mode-route').addEventListener('click', () => {
    state.mode = 'route';
    render();
});

el('search-go').addEventListener('click', search);
el('search').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') search();
});

el('undo').addEventListener('click', () => {
    state.waypoints.pop();
    render();
});
el('clear').addEventListener('click', () => {
    state.waypoints = [];
    render();
});

el('speed').addEventListener('input', (event) => {
    state.speedKmh = Number(event.target.value);
    renderPanel();
});
document.querySelectorAll('.chip-button').forEach((button) => {
    button.addEventListener('click', () => {
        state.speedKmh = Number(button.dataset.speed);
        el('speed').value = String(state.speedKmh);
        renderPanel();
    });
});
el('loop').addEventListener('change', (event) => {
    state.loop = event.target.checked;
    render();
});
el('accuracy').addEventListener('input', (event) => {
    state.accuracy = Number(event.target.value);
    renderPanel();
});

el('start').addEventListener('click', () => {
    const points = state.mode === 'fixed' ? (state.target ? [state.target] : []) : state.waypoints;
    post({
        type: 'start',
        serial: state.serial,
        isEmulator: state.isEmulator,
        points,
        speedKmh: state.speedKmh,
        loop: state.loop,
        accuracy: state.accuracy,
    });
    if (!bridge) {
        state.running = true;
        state.spoofed = points[0] || null;
        render();
    }
});
el('stop').addEventListener('click', () => {
    post({ type: 'stop', serial: state.serial, isEmulator: state.isEmulator });
    if (!bridge) {
        state.running = false;
        state.spoofed = null;
        render();
    }
});

/* --------------------------------------------------------------- start --- */

if (bridge) {
    bridge.addEventListener('message', (event) => handle(event.data));
    post({ type: 'ready' });
} else {
    el('preview-banner').classList.remove('hidden');
    el('adb-path').textContent = 'Preview mode: this page is not connected to adb.';
    setDevices([{ serial: 'preview', label: 'Preview device', ready: true, isEmulator: false }]);
}

render();
