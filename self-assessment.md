---
layout: default
title: Professional Self-Assessment
---

# Professional Self-Assessment

Completing the Computer Science program at Southern New Hampshire University, and building this ePortfolio as its capstone, has been a process of turning scattered coursework into a coherent professional identity. Looking back across courses in mobile architecture, full-stack web development, database design, and secure coding, I can see a consistent thread: I am drawn to the parts of software engineering where correctness and security intersect — where a design decision isn't just about making something work, but about making sure it keeps working safely as it grows.

**Collaborating in a team environment.** Coursework such as CS 250 (Software Development Lifecycle) and my full-stack development work in CS 465 pushed me to think about code the way a team would — through structured commits, incremental milestones, and documentation that someone other than me could pick up. Even working independently on most assignments, I practiced the habits that collaboration requires: writing code reviews that explain *why* a change was made, not just *what* changed, and keeping design documents current so decisions are traceable.

**Communicating with stakeholders.** My software design documents, journal reflections, and this ePortfolio itself are all exercises in adapting technical detail to an audience. A design document written for an instructor, code comments written for a future maintainer, and a self-assessment written for a hiring manager all require the same underlying facts to be reframed for different readers. That skill — translating technical decisions into their business or user impact — is one I intentionally built throughout the program.

**Data structures and algorithms.** The algorithms enhancement in this ePortfolio reflects coursework from CS 260 and CS 320, where I moved from simply calling built-in language features to reasoning about complexity and trade-offs directly. Implementing binary search, custom sorting, and a heap-based grouping utility by hand — instead of relying on a library default — forced me to think about *why* an approach is efficient, not just whether it produces a correct answer.

**Software engineering and database design.** Across CS 340, CS 465, and CS 499, I've worked with both relational (SQLite) and NoSQL (MongoDB) data models, and learned to treat the database layer as a first-class part of the architecture rather than an afterthought — including schema versioning, migrations, indexing, and audit logging.

**Security.** A security mindset is the throughline of my technical growth. In courses like CS 405 (Secure Coding) and in my capstone enhancements, I've practiced treating security as a design constraint from the start: hashing and salting credentials rather than storing them in plaintext, scoping data access by authenticated user, and building audit trails that make suspicious activity visible rather than assuming good behavior. I'm currently pursuing CompTIA Security+ certification to formalize this focus as I enter the field.

## How the Artifacts Fit Together

This ePortfolio centers on a single artifact — an Android Inventory Management application originally built in CS 360 — enhanced across three milestones that map to the three pillars of this capstone:

1. **Software Design & Engineering** — replacing plaintext credential handling with salted SHA-256 password hashing and a versioned database migration strategy.
2. **Algorithms & Data Structures** — adding binary search, custom sorting, and max-heap-based grouping utilities, backed by unit tests.
3. **Databases** — introducing user-scoped data access, a session manager, and an audit-log table with proper indexing.

Rather than three disconnected projects, these enhancements tell one story: taking a working but naive student project and hardening it into something closer to production-quality software — the kind of transformation I want to keep making throughout my career.
