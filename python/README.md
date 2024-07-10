# BLINK Embedding

This part of the repository contains the source code for the embedding process, training files, datasets, blank node mappings, created models and a CSV of computed embedding vectors for each entity

We use the DICE Embeddings libary version 0.0.5 (https://pypi.org/project/dicee/0.0.5/) for creating the embeddings.

Datasets/: The LinkedGeoData subsets

Experiments/: Configuration files for the embedding library, the embedded models, CSV of computed embedding vectors for each entity and relation

KGs/: The training files computed by the preprocessing, the mapping files, and the evaluation files

main.py: The main class for our embedding process. Places where directory locations have to be changed are marked with #TODO

