#!/usr/bin/env python3
"""
Strip third-party sponsor advertisement sections and funding files.
"""
import os
import glob

def clean_readmes():
    sections = [
        ('README.md', '## Sponsor', '## Overview'),
        ('README_CN.md', '## 赞助商', '## 功能特性'),
        ('README_JA.md', '## スポンサー', '## 概要')
    ]
    for fname, start_kw, end_kw in sections:
        if not os.path.exists(fname):
            continue
        with open(fname, 'r', encoding='utf-8') as f:
            content = f.read()
        start_idx = content.find(start_kw)
        end_idx = content.find(end_kw)
        if start_idx != -1 and end_idx != -1:
            new_content = content[:start_idx].rstrip() + '\n\n' + content[end_idx:]
            with open(fname, 'w', encoding='utf-8', newline='\n') as f:
                f.write(new_content)
            print(f"Cleaned sponsor section from {fname}")

def clean_assets():
    # Remove all sponsor image files in assets/ root (keep assets/logo/)
    for ext in ('*.png', '*.jpg', '*.jpeg'):
        for img in glob.glob(os.path.join('assets', ext)):
            try:
                os.remove(img)
                print(f"Removed sponsor asset: {img}")
            except OSError:
                pass

    funding = os.path.join('.github', 'FUNDING.yml')
    if os.path.exists(funding):
        os.remove(funding)
        print("Removed .github/FUNDING.yml")

if __name__ == '__main__':
    clean_readmes()
    clean_assets()
