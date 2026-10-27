# Stage 2: Medicine Packaging Model Training & Evaluation Report

## 1. Overview
- **Architecture**: Multi-scale Visual Feature Representation + Multinomial Calibrated Classifier
- **Classes Count**: 150 verified medicine packaging classes
- **Total Images**: 3900 (2,700 train / 600 val / 600 test)
- **Feature Dimension**: 1504 descriptors per image
- **Training Duration**: 1.07 seconds

## 2. Evaluation Results on Held-Out Test Set (Unseen Camera Hardware)
| Metric | Score (Decimal) | Percentage |
| :--- | :---: | :---: |
| **Top-1 Accuracy** | `0.0217` | **2.17%** |
| **Top-3 Accuracy** | `0.0667` | **6.67%** |
| **Top-5 Accuracy** | `0.1` | **10.00%** |
| **Macro Precision** | `0.0268` | **2.68%** |
| **Macro Recall** | `0.0217` | **2.17%** |
| **Macro F1-Score** | `0.0155` | **1.55%** |
| **Weighted F1-Score** | `0.0155` | **1.55%** |

## 3. Top Confused Pairs in Test Set
| True Class | Predicted Class | Confused Count |
| :--- | :--- | :---: |
| `Adol 24 caplets` | `La-vie nan gel` | 2 |
| `All-Vent 125 ml syrup` | `Asmakast 30 tablets` | 2 |
| `Amrizole 20 tablets` | `Augmentin 14 tablets` | 2 |
| `Anuva 10 tablets` | `Gengigel nan hydrogel` | 2 |
| `Apidone nan syrup` | `Feroglobin nan capsules` | 2 |
| `B.B.C. 25 ml spray solution` | `La-vie nan gel` | 2 |
| `Betadine 120 ml solution` | `Feroglobin nan capsules` | 2 |
| `Betadine 60  g ointment` | `Feroglobin nan capsules` | 2 |
| `Betadine 120 ml mouth wash` | `La-vie nan gel` | 2 |
| `Bronchicum Elixir 100 ml nan` | `Pentamix 120 ml syrup` | 2 |

## 4. Reproducible Command
To reproduce training and evaluation:
```bash
python ai_model/train_packaging_model.py
```
