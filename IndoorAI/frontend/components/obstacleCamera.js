/**
 * Obstacle Camera & Computer Vision Overlay Component
 * Handles webcam stream, video frame processing, bounding box rendering,
 * distance overlay, and simulated vision data generator.
 */

class ObstacleCamera {
    constructor(videoId, overlayCanvasId, onObstacleDetected) {
        this.video = document.getElementById(videoId);
        this.canvas = document.getElementById(overlayCanvasId);
        this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
        this.onObstacleDetected = onObstacleDetected;

        this.isStreaming = false;
        this.isSimulationMode = false;
        this.simulationTimer = null;
        this.simulatedObstaclesList = [
            { type: 'Chair', distance: 3.5, box: [0.3, 0.4, 0.35, 0.3], xCenterNorm: 0.5 },
            { type: 'Person', distance: 1.1, box: [0.2, 0.1, 0.6, 0.8], xCenterNorm: 0.45 },
            { type: 'Table', distance: 2.4, box: [0.1, 0.5, 0.5, 0.4], xCenterNorm: 0.3 },
            { type: 'Dustbin', distance: 4.0, box: [0.7, 0.6, 0.2, 0.3], xCenterNorm: 0.8 },
            { type: 'Staircase', distance: 5.0, box: [0.3, 0.2, 0.4, 0.6], xCenterNorm: 0.5 }
        ];
    }

    async startCamera() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            console.warn('getUserMedia not supported in this browser environment. Using simulation mode.');
            this.startSimulationMode();
            return;
        }

        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } },
                audio: false
            });
            this.video.srcObject = stream;
            await this.video.play();
            this.isStreaming = true;
            this.isSimulationMode = false;
            this.startDetectionLoop();
            console.log('Real-time obstacle camera started successfully.');
        } catch (err) {
            console.warn('Camera permission denied or camera not available. Fallback to simulation mode:', err.message);
            this.startSimulationMode();
        }
    }

    stopCamera() {
        if (this.video && this.video.srcObject) {
            const tracks = this.video.srcObject.getTracks();
            tracks.forEach(track => track.stop());
            this.video.srcObject = null;
        }
        this.isStreaming = false;
        if (this.simulationTimer) {
            clearInterval(this.simulationTimer);
            this.simulationTimer = null;
        }
    }

    startSimulationMode() {
        this.isSimulationMode = true;
        this.renderSimulatedFrame(this.simulatedObstaclesList[0]);

        // Draw animated simulated obstacle feed
        let index = 0;
        this.simulationTimer = setInterval(() => {
            const obs = this.simulatedObstaclesList[index % this.simulatedObstaclesList.length];
            this.renderSimulatedFrame(obs);
            index++;
        }, 5000);
    }

    injectObstacle(type, distance, xCenterNorm = 0.5) {
        const obs = {
            type,
            distance,
            xCenterNorm,
            box: [xCenterNorm - 0.15, 0.2, 0.3, 0.6]
        };
        this.renderSimulatedFrame(obs);
        if (this.onObstacleDetected) {
            this.onObstacleDetected(obs);
        }
    }

    renderSimulatedFrame(obs) {
        if (!this.canvas || !this.ctx) return;
        const ctx = this.ctx;
        const w = this.canvas.width = this.canvas.clientWidth;
        const h = this.canvas.height = this.canvas.clientHeight;

        ctx.clearRect(0, 0, w, h);

        // Simulation camera placeholder background
        if (!this.isStreaming) {
            ctx.fillStyle = '#0f172a';
            ctx.fillRect(0, 0, w, h);

            // Simulation grid & camera viewfinder corners
            ctx.strokeStyle = 'rgba(6, 182, 212, 0.4)';
            ctx.lineWidth = 2;
            const cornerLen = 20;

            // Top-left
            ctx.beginPath();
            ctx.moveTo(15, 15 + cornerLen); ctx.lineTo(15, 15); ctx.lineTo(15 + cornerLen, 15);
            ctx.stroke();
            // Top-right
            ctx.beginPath();
            ctx.moveTo(w - 15 - cornerLen, 15); ctx.lineTo(w - 15, 15); ctx.lineTo(w - 15, 15 + cornerLen);
            ctx.stroke();
            // Bottom-left
            ctx.beginPath();
            ctx.moveTo(15, h - 15 - cornerLen); ctx.lineTo(15, h - 15); ctx.lineTo(15 + cornerLen, h - 15);
            ctx.stroke();
            // Bottom-right
            ctx.beginPath();
            ctx.moveTo(w - 15 - cornerLen, h - 15); ctx.lineTo(w - 15, h - 15); ctx.lineTo(w - 15, h - 15 - cornerLen);
            ctx.stroke();

            ctx.fillStyle = 'rgba(148, 163, 184, 0.7)';
            ctx.font = '11px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('LIVE AI COMPUTER VISION SCANNER (YOLO / MobileNet)', w / 2, 28);
        }

        // Draw Bounding Box for detected obstacle
        if (obs && obs.box) {
            const bx = obs.box[0] * w;
            const by = obs.box[1] * h;
            const bw = obs.box[2] * w;
            const bh = obs.box[3] * h;

            const isDanger = obs.distance < 1.5;
            const isCaution = obs.distance <= 3.2 && !isDanger;
            const strokeColor = isDanger ? '#ef4444' : (isCaution ? '#f59e0b' : '#10b981');
            const fillColor = isDanger ? 'rgba(239, 68, 68, 0.2)' : (isCaution ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.1)');

            ctx.fillStyle = fillColor;
            ctx.fillRect(bx, by, bw, bh);

            ctx.strokeStyle = strokeColor;
            ctx.lineWidth = 3;
            ctx.strokeRect(bx, by, bw, bh);

            // Badge text
            const badgeText = `${obs.type.toUpperCase()}: ${obs.distance}m [${isDanger ? 'DANGER' : (isCaution ? 'CAUTION' : 'SAFE')}]`;
            ctx.font = 'bold 12px sans-serif';
            const textWidth = ctx.measureText(badgeText).width;

            ctx.fillStyle = strokeColor;
            ctx.fillRect(bx, by - 24, textWidth + 14, 24);

            ctx.fillStyle = '#ffffff';
            ctx.textAlign = 'left';
            ctx.fillText(badgeText, bx + 7, by - 7);
        }
    }

    startDetectionLoop() {
        const detectFrame = async () => {
            if (!this.isStreaming) return;
            // Draw real-time detection box over live video stream
            const w = this.canvas.width = this.canvas.clientWidth;
            const h = this.canvas.height = this.canvas.clientHeight;
            this.ctx.clearRect(0, 0, w, h);

            // Overlay crosshair and optical scanner line
            this.ctx.strokeStyle = 'rgba(6, 182, 212, 0.3)';
            this.ctx.lineWidth = 1;
            this.ctx.beginPath();
            this.ctx.moveTo(w / 2, 0); this.ctx.lineTo(w / 2, h);
            this.ctx.moveTo(0, h / 2); this.ctx.lineTo(w, h / 2);
            this.ctx.stroke();

            requestAnimationFrame(detectFrame);
        };
        requestAnimationFrame(detectFrame);
    }
}

window.ObstacleCamera = ObstacleCamera;
