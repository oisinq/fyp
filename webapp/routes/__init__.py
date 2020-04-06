from flask import Blueprint

routes = Blueprint('routes', __name__)
from .routing_service import *
from .knn_availability_service import *
from .history_service import *
from .feedback_service import *
