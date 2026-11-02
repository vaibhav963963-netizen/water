# Live WaterPaper - Fluid Physics & Simulation Design

This document describes the computational physics, numerical methods, sensor filtering, and mathematical approximations implemented in Live WaterPaper.

---

## 1. Fluid Solver Selection

To achieve fluid motion on mobile hardware (targeting 60–120 FPS on devices like the iQOO Neo 6) while replicating the organic deformation and droplet behavior of Live WaterPaper, we selected a **Smoothed Particle Hydrodynamics (SPH)** model augmented with a **Tait equation** pressure state.

### Why SPH over Eulerian Grids on Mobile?
1. **Dynamic Boundaries**: Eulerian grid solvers require computing full grid pressure matrices (e.g. Jacobi or Conjugate Gradient iterations) which are computationally expensive on mobile GPUs and struggle with detached, flying droplets without complex volume-of-fluid (VOF) reconstruction.
2. **Natural Droplet Separation & Merging**: In SPH, droplets are simply particles with few or no neighbors. They naturally detach under high shear, fly under gravity, and re-merge into the pool when contact is re-established.
3. **Exact Mass Conservation**: Since particles represent fixed fluid mass quanta, liquid volume remains perfectly conserved during long wallpaper runs without volume loss or grid numerical dissipation.

---

## 2. Mathematical Model

### 2.1 Density Estimation
For each particle $i$, the local fluid density $\rho_i$ is evaluated by summing smoothed contributions from neighboring particles $j$ within the smoothing interaction radius $h$:

$$\rho_i = \sum_{j} W(r_{ij}, h)$$

We utilize a cubic polynomial smoothing kernel $W(r, h)$:

$$W(r, h) = \begin{cases} \left(1 - \frac{r^2}{h^2}\right)^3 & \text{if } 0 \le r < h \\ 0 & \text{if } r \ge h \end{cases}$$

### 2.2 Pressure Projection (Tait Equation)
Rather than solving an iterative Poisson system on the CPU, we evaluate pressure using the modified Tait equation for quasi-incompressible fluids:

$$p_i = k \cdot (\rho_i - \rho_0)$$

Where:
- $k$: Fluid bulk modulus / stiffness constant ($k \approx 14,000$).
- $\rho_0$: Fluid rest density ($\rho_0 \approx 3.5$).

### 2.3 Pressure Gradient & Force
The pressure force acting between particle $i$ and neighboring particles $j$ is calculated using a symmetric gradient formulation to ensure Newton's third law ($\vec{F}_{ij} = -\vec{F}_{ji}$):

$$\vec{F}_i^{\text{pressure}} = -\sum_{j \ne i} \frac{p_i + p_j}{2 \rho_j} \nabla W(r_{ij}, h)$$

Where $\nabla W(r, h) = -\frac{\vec{r}_{ij}}{r_{ij}} \cdot \left(1 - \frac{r_{ij}}{h}\right)^2$.

### 2.4 Viscous Shear & Damping
Internal viscosity prevents numerical oscillations and models fluid cohesion:

$$\vec{F}_i^{\text{viscosity}} = \mu \sum_{j \ne i} \frac{\vec{v}_j - \vec{v}_i}{\rho_j} \cdot \left(1 - \frac{r_{ij}}{h}\right)$$

Where $\mu$ is the dynamic fluid viscosity coefficient ($\mu \approx 85$).

---

## 3. Gravity & Sensor Processing

### 3.1 Device Orientation Mapping
Orientation is acquired using Android's `Sensor.TYPE_ROTATION_VECTOR` when available, with an automatic fallback to `Sensor.TYPE_ACCELEROMETER`.

Raw sensor measurements are rotated according to the device display orientation:
- **Portrait ($0^\circ$)**: $g_x = \text{raw}_x \cdot 9.8$, $g_y = \text{raw}_y \cdot 9.8$
- **Landscape ($90^\circ$)**: $g_x = -\text{raw}_y \cdot 9.8$, $g_y = \text{raw}_x \cdot 9.8$
- **Reverse Portrait ($180^\circ$)**: $g_x = -\text{raw}_x \cdot 9.8$, $g_y = -\text{raw}_y \cdot 9.8$
- **Reverse Landscape ($270^\circ$)**: $g_x = \text{raw}_y \cdot 9.8$, $g_y = -\text{raw}_x \cdot 9.8$

### 3.2 Low-Pass Filter vs Dynamic Inertia
A complementary low-pass filter isolates steady gravity from dynamic linear acceleration:

$$\vec{g}_{\text{filtered}}(t) = \alpha \cdot \vec{g}_{\text{filtered}}(t-1) + (1 - \alpha) \cdot \vec{a}_{\text{raw}}(t)$$

Where $\alpha = 0.82$.

The dynamic linear acceleration (the motion of user shaking or flicking the phone) is:

$$\vec{a}_{\text{dynamic}} = \vec{a}_{\text{raw}} - \vec{g}_{\text{filtered}}$$

### 3.3 Shake & Splash Detection
A shake event is detected when:

$$\|\vec{a}_{\text{dynamic}}\| > \text{threshold}_{\text{shake}} \quad (\approx 14.0 \, \text{m/s}^2)$$

To prevent repeated false triggers from subtle hand tremors, a **320 ms cooldown period** is enforced. When triggered, a directional splash impulse is injected into the upper fluid layer, creating authentic surface sloshing.

---

## 4. Droplets & Surface Tension

### 4.1 Droplet Separation Mechanism
Liquid does not break into droplets uniformly. Detachment occurs under two realistic physical conditions:
1. **High Dynamic Kinetic Energy**: When an energetic shake or rapid tilt occurs, particles with velocity exceeding $v_{\text{detach}} \approx 800 \, \text{px/s}$ detach.
2. **Surface Shear & Low Local Density**: Particles located on the surface boundary with local density $\rho_i < 0.42 \cdot \rho_0$ that possess upward momentum separate into independent droplet entities.

Detached droplets:
- Have a reduced visual radius ($0.4 \dots 0.75 \times r_{\text{particle}}$).
- Move ballistically influenced by gravity, momentum, and air drag ($v_{\text{drag}} = 0.996$).
- Stretch slightly along their velocity vector.

### 4.2 Droplet Merging & Reconnection
When a falling or sliding droplet approaches the main fluid body within the merging radius:

$$\|\vec{x}_{\text{droplet}} - \vec{x}_{\text{fluid}}\| < R_{\text{merge}} \quad (R_{\text{merge}} \approx 28 \, \text{px})$$

The droplet is seamlessly absorbed:
- The droplet flag is cleared: `isDroplet = false`.
- Radius is smoothly restored to the standard fluid particle radius.
- Momentum is averaged with the contact particle, preventing abrupt velocity discontinuities.

---

## 5. Numerical Stability & Boundaries

### 5.1 Clamped Time Stepping
To protect against time delta spikes when an application resumes or undergoes a garbage collection pause:

$$\Delta t = \text{clamp}(\Delta t_{\text{measured}}, 0.001 \, \text{s}, 0.033 \, \text{s})$$

### 5.2 Velocity Clamping
Maximum particle velocity is clamped to $2,200 \, \text{px/s}$ to satisfy the Courant-Friedrichs-Lewy (CFL) stability condition:

$$\Delta t < \frac{h}{v_{\text{max}}}$$

### 5.3 Boundary Restitution
When particles collide with screen borders (left, right, top, bottom):

$$x = \text{clamp}(x, r, W - r), \quad v_x = -v_x \cdot \epsilon$$

Where $\epsilon = 0.38$ is the restitution coefficient, simulating energy absorption and dampening wave reflections.

---

## 6. Physical Approximations & Limitations

1. **2D Projection**: The simulation solves a 2D projection corresponding to the flat smartphone screen surface.
2. **Simplified Surface Tension**: Rather than computing second-derivative Laplace-Beltrami curvature operators, surface tension is approximated via density gradient repulsion and neighbor cohesion.
3. **Quasi-Incompressibility**: The Tait equation allows minute density fluctuations ($< 5\%$) to enable explicit time stepping without iterative linear solvers.
