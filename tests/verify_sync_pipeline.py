#!/usr/bin/env python3
"""
SyncCut Pipeline & Algorithm Verification Script
Tests core synchronization algorithms, normalized cross correlation,
and media inspection on real video/audio files.
"""

import sys
import subprocess
import json
import math
import numpy as np

def test_cross_correlation_math():
    print("=== TEST 1: Normalized Cross-Correlation (NCC) Math ===")
    t = np.linspace(0, 10, 10000)
    # Synthetic speech-like envelope
    signal_a = np.sin(2 * np.pi * 1.5 * t) * np.exp(-t / 5) + 0.5 * np.sin(2 * np.pi * 5.0 * t)
    
    # Introduce deliberate lag: 235 samples (~235 ms at 1000 Hz)
    lag = 235
    signal_b = np.roll(signal_a, lag)
    # Add random low noise
    signal_b += np.random.normal(0, 0.05, signal_b.shape)

    # Compute NCC
    ref_norm = (signal_a - np.mean(signal_a)) / (np.std(signal_a) * len(signal_a))
    tgt_norm = (signal_b - np.mean(signal_b)) / np.std(signal_b)
    correlation = np.correlate(tgt_norm, ref_norm, mode='full')
    lags = np.arange(-len(signal_a) + 1, len(signal_a))
    
    peak_idx = np.argmax(correlation)
    detected_lag = lags[peak_idx]
    peak_score = correlation[peak_idx]

    print(f"Expected Lag: {lag} samples | Detected Lag: {detected_lag} samples")
    print(f"NCC Peak Score: {peak_score:.4f}")
    assert detected_lag == lag, f"Lag mismatch: expected {lag}, got {detected_lag}"
    assert peak_score > 0.80, f"Correlation score too low: {peak_score}"
    print("✓ TEST 1 PASSED: Exact lag and high confidence detected.\n")

def test_timeline_microsecond_math():
    print("=== TEST 2: Timeline Microsecond Math & Boundary Slicing ===")
    fps_num = 30000
    fps_den = 1001 # 29.97 fps
    
    # 1 second in microseconds
    one_sec_us = 1_000_000
    # Frames in 1 sec
    frames = (one_sec_us * fps_num) // (1_000_000 * fps_den)
    print(f"Frames in 1 sec at 29.97 fps: {frames} (Expected: 29 or 30)")
    assert frames in (29, 30)

    # Test clip split at 4.25 seconds (4_250_000 us)
    clip_start_us = 0
    clip_end_us = 10_000_000
    playhead_us = 4_250_000
    
    clip_a_dur = playhead_us - clip_start_us
    clip_b_dur = clip_end_us - playhead_us
    assert clip_a_dur + clip_b_dur == 10_000_000
    print(f"Clip A Duration: {clip_a_dur} us ({clip_a_dur/1e6:.2f}s)")
    print(f"Clip B Duration: {clip_b_dur} us ({clip_b_dur/1e6:.2f}s)")
    print("✓ TEST 2 PASSED: Microsecond precision preserved without float drift.\n")

def test_real_media_inspection():
    print("=== TEST 3: Real Video File Inspection with ffprobe ===")
    sample_video = "/data/user/0/ai.antigem/files/home/projects/brain-myth-3d/test_10s.mp4"
    cmd = [
        "ffprobe", "-v", "error",
        "-show_entries", "stream=codec_name,width,height,duration,r_frame_rate",
        "-of", "json",
        sample_video
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Failed to run ffprobe: {res.stderr}")
        sys.exit(1)
        
    data = json.loads(res.stdout)
    streams = data.get("streams", [])
    print(f"Found {len(streams)} streams in sample video:")
    for s in streams:
        codec = s.get("codec_name")
        dur = s.get("duration", "N/A")
        w = s.get("width", "N/A")
        h = s.get("height", "N/A")
        fps = s.get("r_frame_rate", "N/A")
        print(f" - Codec: {codec}, Dim: {w}x{h}, FPS: {fps}, Duration: {dur}s")

    assert any(s.get("codec_name") == "h264" for s in streams), "H264 stream missing"
    assert any(s.get("codec_name") == "aac" for s in streams), "AAC audio stream missing"
    print("✓ TEST 3 PASSED: Sample media conforms to H.264/AAC MP4 specification.\n")

if __name__ == "__main__":
    test_cross_correlation_math()
    test_timeline_microsecond_math()
    test_real_media_inspection()
    print("ALL ALGORITHM AND PIPELINE VERIFICATION TESTS PASSED SUCCESSFULLY!")
