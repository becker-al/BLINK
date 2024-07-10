import dicee.static_funcs
from dicee import Execute, KGE
import pytorch_lightning as pl
from dicee.static_funcs import *
from dicee.executer import Execute
from dicee.config import Namespace


# Sets the correct number of epochs
def initialize_trainer(args, callbacks):
    print('Initializing Pytorch-lightning Trainer', end='\t')
    return pl.Trainer(accelerator="gpu", devices=1, max_epochs=args.args.max_epochs)


def main():
    # TODO Add training directory names. For example KGs/Synthetic/Random-R7-L4
    for folder in (

    ):
        models = {"TransE"}
        for epochs in (16, 32, 64, 128, 256,):
            for dim in (32,):
                for model in models:
                    print("START FOR ", epochs, " EPOCHS")
                    global maskAG
                    global maskBG
                    global newValsAG
                    global newValsBG
                    maskAG = None
                    maskBG = None
                    newValsAG = None
                    newValsBG = None
                    embed_and_match(model, epochs, folder,
                                    embedding_dim=dim)


def embed_and_match(model, epochs, graph, embedding_dim=32, batch_size=1024):
    # Arguments
    args = Namespace()
    args.storage_path = "Experiments/" + graph.split("/")[-1] + "/" + model + "-" + str(epochs) + "-" + str(batch_size)
    args.trainer = "PL"
    args.num_core = 1
    args.gpus = 1
    args.save_embeddings_as_csv = True
    args.model = model
    args.max_epochs = epochs
    args.path_dataset_folder = graph
    args.embedding_dim = embedding_dim
    args.num_epochs = epochs
    args.batch_size = batch_size
    args.scoring_technique = "KvsAll"
    args.eval_model = "None"
    args.seed_for_computation = 1

    # Start embedding process
    executor = Execute(args)
    report = executor.start()

    # Extract embedding vectors
    pre_trained_model = KGE(path=report['path_experiment_folder'])
    pre_trained_model.entity_to_idx.keys()
    entities = [s for s in pre_trained_model.entity_to_idx.keys()]
    vectors = pre_trained_model.get_entity_embeddings(entities)
    cuda_device = torch.device('cuda:0')
    vectors = vectors.to(cuda_device)

    # Get embedding vectors for blank nodes
    full_dict = dict(zip(entities, vectors))
    blankA_dict = {key: full_dict[key] for key in entities if
                   str.startswith(key, "<■0■") or str.startswith(key, "<BlankNode#A")}
    blankB_dict = {key: full_dict[key] for key in entities if
                   str.startswith(key, "<■1■") or str.startswith(key, "<BlankNode#B")}

    # Not necessary / does not change anything. Just for easier debugging
    a_sorted = dict(sorted(blankA_dict.items()))
    b_sorted = dict(sorted(blankB_dict.items()))

    print("ComputeMapping")
    writeMappingToFile(
        args.path_dataset_folder + '/mapping-' + model + '-epochs-' + (
                '%04d' % epochs) + '-dim-' + ('%03d' % embedding_dim) + '.txt'
        , compute_mapping(a_sorted, b_sorted))
    print("Done Computing Mappings")


# Writes a given mapping to the file
def writeMappingToFile(path, mapping):
    file = open(path, 'w')
    for (k, v) in mapping:
        file.writelines(k + ">-<" + v + "\n")
    file.close()


def compute_mapping(blankA_dict, blankB_dict):
    mapping = list()
    start = time.time()

    # Gets the location of blank nodes from graph A in the vector space and puts them into a tensor
    aTensorKeys = list(blankA_dict.keys())
    aTensorFull = torch.stack(list(blankA_dict.values()))

    # Gets the location of blank nodes from graph B in the vector space and puts them into a tensor
    bTensorKeys = list(blankB_dict.keys())
    bTensorFull = torch.stack(list(blankB_dict.values()))

    # Enable broadcasting for further computations
    tensor1 = aTensorFull.unsqueeze(0)
    tensor2 = bTensorFull.unsqueeze(1)

    # Calculate the distance between everything
    distance = tensor1.sub(tensor2).pow_(2).sum(dim=-1)

    print(distance.shape)
    print(distance)

    for i in range(0, distance.size(0)):
        smallest_value = torch.min(distance)
        # Calculate the row and column for the smallest distance
        row_idx, col_idx = torch.where(distance == smallest_value)

        # If there are multiple occurrences of the smallest value, row_idx and col_idx will contain all of them
        # For the moment we just take the first and not decide which one to take
        smallest_row_index = row_idx.tolist()[0]
        smallest_col_index = col_idx.tolist()[0]

        # for debugging:
        # if aTensorKeys[smallest_row_index][4:] != bTensorKeys[smallest_col_index][4:]:
        #    print("Mismatch at iteration ", i, ": ", aTensorKeys[smallest_row_index], " <-> ",
        #          bTensorKeys[smallest_col_index])
        #    print("Diff: ", smallest_value)
        #    try:
        #        print("DiffRow: ",
        #              distance[smallest_row_index][bTensorKeys.index("<■1■" + aTensorKeys[smallest_row_index][4:])])
        #    except:
        #        print("Row already matched with something else")
        #    try:
        #        print("DiffCol: ",
        #              distance[aTensorKeys.index("<■0■" + bTensorKeys[smallest_col_index][4:])][smallest_col_index])
        #    except:
        #        print("Col already matched with something else")
        #    print("-------")

        # Get the ids for the blank nodes that are closest to each other
        minA = aTensorKeys[smallest_row_index]
        minB = bTensorKeys[smallest_col_index]

        # Delete newly found pair from the list of nodes not yet matched
        del aTensorKeys[smallest_row_index]
        del bTensorKeys[smallest_col_index]

        # Add found pair to the final mapping
        mapping.append((minA, minB))

        # Remove the row and col from the distance matrix, so we only match everything once
        distance = torch.cat((
            torch.cat((distance[:smallest_row_index, :smallest_col_index],
                       distance[:smallest_row_index, smallest_col_index + 1:]), dim=1),
            torch.cat((distance[smallest_row_index + 1:, :smallest_col_index],
                       distance[smallest_row_index + 1:, smallest_col_index + 1:]), dim=1)
        ))

        torch.cuda.empty_cache()

    print("Took: %f " % (time.time() - start))
    return mapping


# Gets the uri for an entity id used by the embedding library
def getUriByIdx(trainer, idxReq):
    return [uri for uri, idx in trainer.dataset.entity_to_idx.items() if idx == idxReq][0]


# Gets the uri for a property id used by the embedding library
def getPropByIdx(trainer, idxReq):
    return [uri for uri, idx in trainer.dataset.relation_to_idx.items() if idx == idxReq][0]


# Checks if a node is a blank node in the original graph A
def isBlankNodeA(uri):
    return str.startswith(uri, "<■0■") or str.startswith(uri, "<BlankNode#A")


# Checks if a node is a blank node in the original graph B
def isBlankNodeB(uri):
    return str.startswith(uri, "<■1■") or str.startswith(uri, "<BlankNode#B")


# Masks for setting the target to 0.5 for bnodes from different graphs
maskAG = None
maskBG = None
newValsAG = None
newValsBG = None


# This method changes the target value for blank nodes from two different graphs
def training_step(self, batch, batch_idx=None):
    blankAIds = {idx for uri, idx in self.trainer.dataset.entity_to_idx.items() if
                 str.startswith(uri, "<■0■") or str.startswith(uri, "<BlankNode#A")}
    blankBIds = {idx for uri, idx in self.trainer.dataset.entity_to_idx.items() if
                 str.startswith(uri, "<■1■") or str.startswith(uri, "<BlankNode#B")}

    x_batch, y_batch = batch

    indicesToReplaceA = [i for i in range(0, x_batch.size(dim=0)) if x_batch[i][0].item() in blankAIds]
    indicesToReplaceB = [i for i in range(0, x_batch.size(dim=0)) if x_batch[i][0].item() in blankBIds]

    global maskAG
    global maskBG
    global newValsAG
    global newValsBG

    if maskAG is None:
        newvaluesA = torch.full((1, len(blankAIds)), 0.5, dtype=torch.float)
        newvaluesB = torch.full((1, len(blankBIds)), 0.5, dtype=torch.float)

        # Create a mask tensor with the same shape as the original tensor
        maskA = torch.zeros_like(y_batch[0], dtype=torch.bool)
        maskB = torch.zeros_like(y_batch[0], dtype=torch.bool)

        # Set the elements at the specified indices to True in the mask
        # maskA[blankAIds] = True
        # maskB[blankBIds] = True
        for idx in blankAIds:
            maskA[idx] = 1
        for idx in blankBIds:
            maskB[idx] = 1

        cuda_device = torch.device("cuda:0")
        newvaluesA = newvaluesA.to(cuda_device)
        newvaluesB = newvaluesB.to(cuda_device)
        maskA = maskA.to(cuda_device)
        maskB = maskB.to(cuda_device)
        maskAG = maskA
        maskBG = maskB
        newValsAG = newvaluesA
        newValsBG = newvaluesB
    else:
        maskA = maskAG
        maskB = maskBG
        newvaluesA = newValsAG
        newvaluesB = newValsBG

    for index in indicesToReplaceA:
        batch_row = y_batch[index]
        batch_row[maskB] = newvaluesA
    for index in indicesToReplaceB:
        batch_row = y_batch[index]
        batch_row[maskA] = newvaluesB

    # Compute output
    yhat_batch = self.forward(x_batch)

    # Create custom loss function if it does not exist yet
    try:
        self.lossf
    except AttributeError:
        self.lossf = CustomLoss()

    # Apply loss function
    loss_batch = self.lossf(yhat_batch, y_batch)
    return loss_batch


class CustomLoss(torch.nn.Module):
    def __init__(self):
        super(CustomLoss, self).__init__()
        self.pos_weight = 1
        print("Create loss function")

    def forward(self, input, target):
        epsilon = 10 ** -44
        input = input.sigmoid()
        input = input.clamp(epsilon, 1 - epsilon)

        bce_loss = -1 * (self.pos_weight * target * torch.log(input)
                         + (1 - target) * torch.log(1 - input))

        ignore_mask = (target - 0.5) ** 2 * 4  # to ignore bb pairs from different graphs

        mean_loss = (bce_loss * ignore_mask).mean()
        loss = mean_loss
        return loss


# For using this on windows
if __name__ == '__main__':
    dicee.models.base_model.BaseKGE.training_step = training_step
    dicee.executer.DICE_Trainer.initialize_trainer = initialize_trainer
    main()
