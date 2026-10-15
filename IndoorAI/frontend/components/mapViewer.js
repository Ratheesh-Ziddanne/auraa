/**
 * Indoor Interactive Map Canvas Component
 * Renders floorplan, rooms, BLE beacons, user position, active route path,
 * and detected obstacles with animated markers.
 */

class IndoorMapViewer {
    constructor(canvasId) {
        this.canvas = document.getElementById(canvasId);
        if (!this.canvas) return;
        this.ctx = this.canvas.getContext('2d');
        
        this.currentFloor = 0;
        this.currentPosition = { x: 10, y: 10, name: 'Block A Entrance' };
        this.activeRoute = null;
        this.activeObstacles = [];
        this.blockedSegments = [];

        this.pulsePhase = 0;
        this.animationFrame = null;

        this.initCanvasSize();
        window.addEventListener('resize', () => this.initCanvasSize());
        this.startAnimationLoop();
    }

    initCanvasSize() {
        if (!this.canvas) return;
        const rect = this.canvas.getBoundingClientRect();
        this.canvas.width = rect.width * window.devicePixelRatio;
        this.canvas.height = rect.height * window.devicePixelRatio;
        this.render();
    }

    setFloor(floorNum) {
        this.currentFloor = parseInt(floorNum, 10);
        this.render();
    }

    setUserPosition(x, y, name) {
        this.currentPosition = { x, y, name };
        this.render();
    }

    setRoute(route) {
        this.activeRoute = route;
        this.render();
    }

    setObstacles(obstacles = []) {
        this.activeObstacles = obstacles;
        this.render();
    }

    startAnimationLoop() {
        const loop = () => {
            this.pulsePhase = (this.pulsePhase + 0.05) % (Math.PI * 2);
            this.render();
            this.animationFrame = requestAnimationFrame(loop);
        };
        this.animationFrame = requestAnimationFrame(loop);
    }

    // Transform normalized campus coordinates [0..100, 0..50] to canvas pixel coordinates
    toPixel(normX, normY) {
        const padding = 40;
        const w = this.canvas.width - padding * 2;
        const h = this.canvas.height - padding * 2;
        return {
            x: padding + (normX / 105) * w,
            y: padding + (normY / 50) * h
        };
    }

    render() {
        if (!this.ctx || !this.canvas) return;
        const ctx = this.ctx;
        const w = this.canvas.width;
        const h = this.canvas.height;

        ctx.clearRect(0, 0, w, h);

        // Background grid pattern
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.04)';
        ctx.lineWidth = 1;
        const step = 30 * window.devicePixelRatio;
        for (let x = 0; x < w; x += step) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, h);
            ctx.stroke();
        }
        for (let y = 0; y < h; y += step) {
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(w, y);
            ctx.stroke();
        }

        // Draw Rooms & Corridors based on Floor
        if (this.currentFloor === 0) {
            this.drawRoom('Block A Entrance', 5, 5, 20, 20, '#10b981', 'Entrance (Safe Zone)');
            this.drawRoom('Main Corridor', 25, 6, 20, 16, '#38bdf8', 'Tactile Main Corridor');
            this.drawRoom('Help Desk', 15, 22, 22, 16, '#f59e0b', 'Reception & Help Desk');
            this.drawRoom('Central Stairs', 45, 5, 18, 16, '#a855f7', 'Stairs to 1F');
            this.drawRoom('Elevator GF', 45, 22, 18, 16, '#6366f1', 'Accessible Elevator');
            this.drawRoom('Cafeteria', 67, 5, 22, 22, '#3b82f6', 'Cafeteria Wing');
            this.drawRoom('Emergency Exit', 90, 5, 15, 20, '#ef4444', 'Emergency Exit A');
        } else {
            this.drawRoom('Stairs Landing 1F', 45, 5, 18, 16, '#a855f7', 'Stairs from GF');
            this.drawRoom('Elevator 1F', 45, 22, 18, 16, '#6366f1', 'Accessible Elevator');
            this.drawRoom('Restroom 1F', 20, 5, 20, 16, '#06b6d4', 'Accessible Restroom');
            this.drawRoom('1F Corridor', 63, 6, 18, 16, '#38bdf8', 'East Corridor');
            this.drawRoom('Computer Lab', 81, 5, 20, 20, '#f59e0b', 'Computer Lab');
            this.drawRoom('Library', 75, 28, 25, 18, '#10b981', 'Silent Study Library');
            this.drawRoom('Emergency Exit 1F', 92, 5, 13, 18, '#ef4444', 'Fire Exit 1F');
        }

        // Draw BLE Beacons
        this.drawBleBeacon(30, 10, 'BLE-01');
        this.drawBleBeacon(50, 10, 'BLE-02');
        this.drawBleBeacon(75, 10, 'BLE-03');

        // Draw Calculated Route Path
        if (this.activeRoute && this.activeRoute.waypoints) {
            this.drawRoutePath(this.activeRoute.waypoints);
        }

        // Draw Active Obstacles
        for (const obs of this.activeObstacles) {
            this.drawObstacle(obs);
        }

        // Draw User Marker
        this.drawUserMarker();
    }

    drawRoom(name, x, y, width, height, accentColor, subtitle = '') {
        const ctx = this.ctx;
        const p1 = this.toPixel(x, y);
        const p2 = this.toPixel(x + width, y + height);
        const w = p2.x - p1.x;
        const h = p2.y - p1.y;

        // Room container fill
        ctx.fillStyle = 'rgba(19, 27, 46, 0.75)';
        ctx.strokeStyle = accentColor;
        ctx.lineWidth = 2 * window.devicePixelRatio;

        ctx.beginPath();
        ctx.roundRect(p1.x, p1.y, w, h, 10 * window.devicePixelRatio);
        ctx.fill();
        ctx.stroke();

        // Room Title
        ctx.fillStyle = '#ffffff';
        ctx.font = `bold ${12 * window.devicePixelRatio}px var(--font-main, sans-serif)`;
        ctx.textAlign = 'center';
        ctx.fillText(name, p1.x + w / 2, p1.y + h / 2 - 2 * window.devicePixelRatio);

        // Subtitle
        if (subtitle) {
            ctx.fillStyle = 'rgba(203, 213, 225, 0.7)';
            ctx.font = `${9 * window.devicePixelRatio}px var(--font-main, sans-serif)`;
            ctx.fillText(subtitle, p1.x + w / 2, p1.y + h / 2 + 12 * window.devicePixelRatio);
        }
    }

    drawBleBeacon(normX, normY, label) {
        const ctx = this.ctx;
        const p = this.toPixel(normX, normY);

        // Ripple pulse
        const rippleR = (12 + Math.sin(this.pulsePhase) * 4) * window.devicePixelRatio;
        ctx.beginPath();
        ctx.arc(p.x, p.y, rippleR, 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(6, 182, 212, 0.35)';
        ctx.lineWidth = 1.5 * window.devicePixelRatio;
        ctx.stroke();

        // Beacon dot
        ctx.beginPath();
        ctx.arc(p.x, p.y, 4 * window.devicePixelRatio, 0, Math.PI * 2);
        ctx.fillStyle = '#06b6d4';
        ctx.fill();

        ctx.fillStyle = 'rgba(6, 182, 212, 0.8)';
        ctx.font = `${8 * window.devicePixelRatio}px var(--font-main, sans-serif)`;
        ctx.textAlign = 'center';
        ctx.fillText(label, p.x, p.y - 8 * window.devicePixelRatio);
    }

    drawRoutePath(waypoints) {
        const ctx = this.ctx;
        const floorWaypoints = waypoints.filter(wp => wp.floor === this.currentFloor);
        if (floorWaypoints.length < 2) return;

        // Path glow
        ctx.strokeStyle = 'rgba(245, 158, 11, 0.35)';
        ctx.lineWidth = 8 * window.devicePixelRatio;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';

        ctx.beginPath();
        for (let i = 0; i < floorWaypoints.length; i++) {
            const p = this.toPixel(floorWaypoints[i].x, floorWaypoints[i].y);
            if (i === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
        }
        ctx.stroke();

        // Active animated route line
        ctx.strokeStyle = '#f59e0b';
        ctx.lineWidth = 3 * window.devicePixelRatio;
        ctx.setLineDash([8 * window.devicePixelRatio, 6 * window.devicePixelRatio]);
        ctx.lineDashOffset = -this.pulsePhase * 8;

        ctx.beginPath();
        for (let i = 0; i < floorWaypoints.length; i++) {
            const p = this.toPixel(floorWaypoints[i].x, floorWaypoints[i].y);
            if (i === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
        }
        ctx.stroke();
        ctx.setLineDash([]); // Reset dash

        // Draw waypoint nodes
        for (let i = 0; i < floorWaypoints.length; i++) {
            const p = this.toPixel(floorWaypoints[i].x, floorWaypoints[i].y);
            ctx.beginPath();
            ctx.arc(p.x, p.y, 5 * window.devicePixelRatio, 0, Math.PI * 2);
            ctx.fillStyle = i === floorWaypoints.length - 1 ? '#10b981' : '#f59e0b';
            ctx.fill();
            ctx.strokeStyle = '#000';
            ctx.lineWidth = 1.5 * window.devicePixelRatio;
            ctx.stroke();
        }
    }

    drawObstacle(obs) {
        const ctx = this.ctx;
        const p = this.toPixel(obs.x || 60, obs.y || 10);
        const color = obs.risk_level === 'DANGER' ? '#ef4444' : '#f59e0b';

        // Warning pulsating halo
        const halo = (14 + Math.sin(this.pulsePhase * 2) * 5) * window.devicePixelRatio;
        ctx.beginPath();
        ctx.arc(p.x, p.y, halo, 0, Math.PI * 2);
        ctx.fillStyle = obs.risk_level === 'DANGER' ? 'rgba(239, 68, 68, 0.25)' : 'rgba(245, 158, 11, 0.2)';
        ctx.fill();

        // Center badge
        ctx.beginPath();
        ctx.arc(p.x, p.y, 8 * window.devicePixelRatio, 0, Math.PI * 2);
        ctx.fillStyle = color;
        ctx.fill();
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2 * window.devicePixelRatio;
        ctx.stroke();

        // Label
        ctx.fillStyle = '#ffffff';
        ctx.font = `bold ${9 * window.devicePixelRatio}px var(--font-main, sans-serif)`;
        ctx.textAlign = 'center';
        ctx.fillText(`! ${obs.type} (${obs.distance}m)`, p.x, p.y - 14 * window.devicePixelRatio);
    }

    drawUserMarker() {
        const ctx = this.ctx;
        const p = this.toPixel(this.currentPosition.x, this.currentPosition.y);

        // Radar pulse ripple
        const pulseR = (16 + Math.sin(this.pulsePhase) * 6) * window.devicePixelRatio;
        ctx.beginPath();
        ctx.arc(p.x, p.y, pulseR, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(6, 182, 212, 0.25)';
        ctx.fill();

        // User center dot
        ctx.beginPath();
        ctx.arc(p.x, p.y, 8 * window.devicePixelRatio, 0, Math.PI * 2);
        ctx.fillStyle = '#06b6d4';
        ctx.fill();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2.5 * window.devicePixelRatio;
        ctx.stroke();

        // Heading direction indicator
        ctx.beginPath();
        ctx.moveTo(p.x, p.y - 8 * window.devicePixelRatio);
        ctx.lineTo(p.x - 5 * window.devicePixelRatio, p.y - 18 * window.devicePixelRatio);
        ctx.lineTo(p.x + 5 * window.devicePixelRatio, p.y - 18 * window.devicePixelRatio);
        ctx.closePath();
        ctx.fillStyle = '#f59e0b';
        ctx.fill();

        // User tag
        ctx.fillStyle = '#ffffff';
        ctx.font = `bold ${10 * window.devicePixelRatio}px var(--font-main, sans-serif)`;
        ctx.textAlign = 'center';
        ctx.fillText('YOU', p.x, p.y + 18 * window.devicePixelRatio);
    }
}

window.IndoorMapViewer = IndoorMapViewer;
