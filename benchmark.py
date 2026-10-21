#!/usr/bin/env python3
"""Public CLI for the modular VECTOR odometry benchmark.

Examples:
  python benchmark.py catalog
  python benchmark.py plan --dataset simul --engine android-pdr
  python benchmark.py run --dataset simul --engine android-pdr --out results/simul
"""
from tools.campaigns.unified import main

if __name__ == "__main__":
    main()
