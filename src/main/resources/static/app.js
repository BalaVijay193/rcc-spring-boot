const form = document.querySelector('#uploadForm');
const fileInput = document.querySelector('#fileInput');
const csvButton = document.querySelector('#csvButton');
const statusBox = document.querySelector('#status');
const routeRows = document.querySelector('#routeRows');

let lastFile = null;

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const file = fileInput.files[0];
    if (!file) {
        return;
    }
    lastFile = file;
    setStatus('Generating draft RCC...');
    csvButton.disabled = true;

    try {
        const body = new FormData();
        body.append('file', file);
        const response = await fetch('/api/rcc', { method: 'POST', body });
        if (!response.ok) {
            throw new Error(await response.text());
        }
        const data = await response.json();
        render(data);
        csvButton.disabled = false;
    } catch (error) {
        setStatus(error.message || 'Unable to generate RCC.');
    }
});

csvButton.addEventListener('click', async () => {
    if (!lastFile) {
        return;
    }
    const body = new FormData();
    body.append('file', lastFile);
    const response = await fetch('/api/rcc.csv', { method: 'POST', body });
    if (!response.ok) {
        setStatus(await response.text());
        return;
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'route-control-chart.csv';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
});

function render(data) {
    document.querySelector('#signalCount').textContent = data.signals.length;
    document.querySelector('#pointCount').textContent = data.points.length;
    document.querySelector('#axleCount').textContent = data.axleCounters.length;
    document.querySelector('#routeCount').textContent = data.routes.length;

    routeRows.innerHTML = '';
    if (!data.routes.length) {
        routeRows.innerHTML = '<tr><td colspan="10" class="empty">No routes were generated.</td></tr>';
    } else {
        data.routes.forEach((route) => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${escapeHtml(route.routeId)}</td>
                <td>${escapeHtml(route.fromSignal)}</td>
                <td>${escapeHtml(route.toSignalOrLine)}</td>
                <td>${escapeHtml(route.direction)}</td>
                <td>${list(route.trackCircuitsOrAxleCounters)}</td>
                <td>${list(route.pointsNormal)}</td>
                <td>${list(route.pointsReverse)}</td>
                <td>${list(route.overlapOrIsolation)}</td>
                <td>${list(route.conflictingRoutes)}</td>
                <td><span class="badge">${escapeHtml(route.reviewStatus)}</span></td>
            `;
            routeRows.appendChild(row);
        });
    }
    setStatus((data.warnings || []).join(' '));
}

function list(values) {
    if (!values || !values.length) {
        return '';
    }
    return values.map(escapeHtml).join('<br>');
}

function setStatus(message) {
    statusBox.hidden = !message;
    statusBox.textContent = message || '';
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}
