# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
- **Primary Audience:** Developers looking to learn, analyze, and evaluate latency compensation techniques (client-side prediction, entity interpolation, and server-side lag compensation).
- **Secondary Audience:** Final end customers and clients to whom the demo will be presented to showcase real-time networking and multiplayer capabilities.

## Product Purpose
To provide a responsive, visually clear, and technically robust 2D top-down multiplayer shooter that demonstrates Bernier's three core latency compensation techniques. Success is defined by a polished sandbox where users can feel and visually inspect how prediction, interpolation, and lag compensation keep the experience smooth and fair, even under high network latency.

## Positioning
An interactive, educational multiplayer sandbox specifically designed to visualize and verify netcode mechanics. Unlike general game templates, its core focus is on the transparency, debuggability, and accuracy of latency compensation algorithms.

## Operating Context
- Deployed as a web application running in modern web browsers.
- Integrates a React-based frontend (using HTML5 Canvas for performance), a Java Spring Boot gateway for session/WebSocket bridging, and an authoritative 64Hz Go game service.

## Capabilities and Constraints
- **Capabilities:**
  - 2D top-down player movement using standard WASD controls.
  - Mouse aiming and shooting with immediate client-side visual feedback.
  - Interactive obstacles and walls to test collision physics and shooting behind cover.
  - Health pools, player respawning, a real-time kill feed, and a basic scoreboard.
  - User controls to inject artificial network lag or packet loss for demonstration purposes.
- **Constraints:**
  - Game server authority running at a strict 64Hz tick rate.
  - WebSocket bridging through Spring Boot.
  - Canvas-based client rendering to minimize DOM overhead during high-tick updates.

## Brand Commitments
- clean, developer-focused aesthetic.
- "server-tick-netcode" naming.

## Evidence on Hand
- Incumbent codebase skeletons in `frontend/` (React + Vite), `gateway/` (Java + Spring Boot), and `service/` (Go).

## Product Principles
- **Algorithmic Transparency:** Latency compensation states (predicted path vs. server confirmation, interpolation buffer delay) should be easy to visualize or toggle.
- **Immediate Responsiveness:** Local player actions must feel instant (predicted client-side) to demonstrate the benefit of prediction.
- **Verification of Correctness:** Give clear feedback on hit registration so clients can confirm that lag-compensated server hits align with what they saw on screen.
- **Production-Grade Simplicity:** Keep codebase architecture clean and well-documented to serve as a high-quality learning reference.
