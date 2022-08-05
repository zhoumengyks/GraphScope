
MKFILE_PATH 		:= $(abspath $(lastword $(MAKEFILE_LIST)))
WORKING_DIR 		:= $(dir $(MKFILE_PATH))
GAE_BUILD_DIR		:= $(WORKING_DIR)/analytical_engine/build
GAE_JDK_DIR			:= $(WORKING_DIR)/analytical_engine/java
GIE_DIR				:= $(WORKING_DIR)/interactive_engine
GL_DIR				:= $(WORKING_DIR)/learning_engine/graph-learn
GL_BUILD_DIR		:= $(WORKING_DIR)/learning_engine/graph-learn/build


VERSION                     ?= 0.1.0
INSTALL_PREFIX              ?= /opt/graphscope

BUILD_TYPE                  ?= release

# GAE build options
NETWORKX                    ?= ON

# testing build option
BUILD_TEST                  ?= OFF

# build java sdk option
ENABLE_JAVA_SDK             ?= ON


ifeq ($(OS),Linux)
	NUMPROC := $(grep -c ^processor /proc/cpuinfo)
else ifeq ($(OS),Darwin)
	NUMPROC := $(sysctl hw.ncpu | awk '{print $2}')
endif

# Only take half as many processors as available
NUMPROC 				:= $(echo "$(NUMPROC)/2"|bc)

ifeq ($(NUMPROC),0)
	NUMPROC = 1
endif 


# .PHONY: all
# all: graphscope

# .PHONY: graphscope
# graphscope: install

.PHONY: gsruntime-image
gsruntime-image:
	$(MAKE) -C $(WORKING_DIR)/k8s/ gsruntime-image VERSION=$(VERSION)

.PHONY: gsvineyard-image
gsvineyard-image:
	$(MAKE) -C $(WORKING_DIR)/k8s/ gsvineyard-image VERSION=$(VERSION)

.PHONY: graphscope-image
graphscope-image:
	$(MAKE) -C $(WORKING_DIR)/k8s/ graphscope-image VERSION=$(VERSION)

.PHONY: jupyter-image
jupyter-image:
	$(MAKE) -C $(WORKING_DIR)/k8s/ jupyter-image VERSION=$(VERSION)

.PHONY: dataset-image
dataset-image:
	$(MAKE) -C $(WORKING_DIR)/k8s/ dataset-image VERSION=$(VERSION)

# bulld graphscope image from source code without wheel package
.PHONY: graphscope-dev-image
graphscope-dev-image:
	$(MAKE) -C $(WORKING_DIR)/k8s/ graphscope-dev-image VERSION=$(VERSION)

.PHONY: graphscope-store-image
graphscope-store-image:
	$(MAKE) -C $(WORKING_DIR)/k8s/ graphscope-store-image VERSION=$(VERSION)

.PHONY: push
push:
	$(MAKE) -C $(WORKING_DIR)/k8s/ push

.PHONY: client
client: gle
	cd $(WORKING_DIR)/python && \
	pip3 install -r requirements.txt -r requirements-dev.txt --user && \
	python3 setup.py build_ext --inplace --user
	pip3 install --user --editable $(WORKING_DIR)/python

.PHONY: coordinator
coordinator:
	cd $(WORKING_DIR)/coordinator && \
	pip3 install -r requirements.txt -r requirements-dev.txt --user && \
	python3 setup.py build_builtin
	if [ ! -d "/var/log/graphscope" ]; then \
		sudo mkdir /var/log/graphscope; \
	fi
	sudo chown -R `id -u`:`id -g` /var/log/graphscope

.PHONY: gae
gae:
	mkdir -p $(GAE_BUILD_DIR) || true;
	cd $(GAE_BUILD_DIR) && \
	cmake 	-DCMAKE_INSTALL_PREFIX=$(INSTALL_PREFIX) \
			-DNETWORKX=$(NETWORKX) \
			-DBUILD_TESTS=${BUILD_TEST} \
			-DENABLE_JAVA_SDK=${ENABLE_JAVA_SDK} \
			..
	$(MAKE) -C $(GAE_BUILD_DIR) -j$(NUMPROC)

ifeq ($(ENABLE_JAVA_SDK), ON)
	cd $(GAE_JDK_DIR) && \
	mvn clean package -DskipTests;
endif

.PHONY: gie
gie:
	cd $(GIE_DIR) && \
	mvn clean package -DskipTests -Drust.compile.mode=$(BUILD_TYPE) -P graphscope,graphscope-assembly
	# install
	# mkdir -p $(WORKING_DIR)/.install_prefix && \
	# tar -xf $(WORKING_DIR)/interactive_engine/assembly/target/graphscope.tar.gz --strip-components 1 -C $(WORKING_DIR)/.install_prefix && \
	# sudo cp -r $(WORKING_DIR)/.install_prefix/* $(INSTALL_PREFIX) && \
	# rm -fr $(WORKING_DIR)/.install_prefix

.PHONY: gle
gle:
	git submodule update --init $(GL_DIR)
	cd $(GL_DIR) && git submodule update --init $(GL_DIR)/third_party/pybind11
	mkdir $(GL_BUILD_DIR) || true
	cd $(GL_BUILD_DIR) && \
	cmake 	-DCMAKE_INSTALL_PREFIX=$(INSTALL_PREFIX) \
			-DWITH_VINEYARD=ON \
			-DTESTING=${BUILD_TEST} \
			..
	$(MAKE) -C $(GL_BUILD_DIR) -j$(NUMPROC)


.PHONY: install
# install: gle client gae gie coordinator
install: gie
# install built GAE
	# $(MAKE) -C $(GAE_BUILD_DIR) install
	#TODO(Jingbo): sudo cp -r $(WORKING_DIR)/k8s/kube_ssh $(INSTALL_PREFIX)/bin/

# ifneq ($(INSTALL_PREFIX), /usr/local)
# 	sudo rm -fr /usr/local/include/graphscope && \
# 	sudo ln -sf $(INSTALL_PREFIX)/bin/* /usr/local/bin/ && \
# 	sudo ln -sfn $(INSTALL_PREFIX)/include/graphscope /usr/local/include/graphscope && \
# 	sudo ln -sf ${INSTALL_PREFIX}/lib/*so* /usr/local/lib && \
# 	sudo ln -sf ${INSTALL_PREFIX}/lib/*dylib* /usr/local/lib && \
# 	if [ -d "${INSTALL_PREFIX}/lib64/cmake/graphscope-analytical" ]; then \
# 		sudo rm -fr /usr/local/lib64/cmake/graphscope-analytical; \
# 		sudo ln -sfn ${INSTALL_PREFIX}/lib64/cmake/graphscope-analytical /usr/local/lib64/cmake/graphscope-analytical; \
# 		sudo mkdir -p ${INSTALL_PREFIX}/lib/cmake; \
# 		sudo cp -r ${INSTALL_PREFIX}/lib64/cmake/* ${INSTALL_PREFIX}/lib/cmake/; \
# 	else \
# 		sudo ln -sfn ${INSTALL_PREFIX}/lib/cmake/graphscope-analytical /usr/local/lib/cmake/graphscope-analytical; \
# 	fi
# endif

# ifeq (${ENABLE_JAVA_SDK}, ON)
# 	install -d ${GAE_JDK_DIR}/grape-runtime/target/native/libgrape-jni.* ${INSTALL_PREFIX}/lib
# 	install -d ${GAE_JDK_DIR}/grape-runtime/target/grape-runtime-0.1-shaded.jar ${INSTALL_PREFIX}/lib
# 	install -d ${GAE_JDK_DIR}/grape_jvm_opts ${INSTALL_PREFIX}/conf
# endif
	tar -xf $(GIE_DIR)/assembly/target/graphscope.tar.gz --strip-components 1 -C $(INSTALL_PREFIX)

# wheels
.PHONY: graphscope-py3-package
graphscope-py3-package:
	$(MAKE) -C $(WORKING_DIR)/k8s/ graphscope-py3-package

.PHONY: graphscope-client-py3-package
graphscope-client-py3-package:
	 $(MAKE) -C $(WORKING_DIR)/k8s/ graphscope-client-py3-package

.PHONY: prepare-client
prepare-client:
	cd $(WORKING_DIR)/python && \
	pip3 install -r requirements.txt --user && \
	pip3 install -r requirements-dev.txt --user && \
	python3 setup.py build_proto

.PHONY: graphscope-docs
graphscope-docs: prepare-client
	$(MAKE) -C $(WORKING_DIR)/docs/ html

.PHONY: test
test: unittest minitest k8stest

.PHONY: unittest
unittest:
	cd $(WORKING_DIR)/python && \
	python3 -m pytest --cov=graphscope --cov-config=.coveragerc --cov-report=xml --cov-report=term -s -v ./graphscope/tests/unittest

.PHONY: minitest
minitest:
	cd $(WORKING_DIR)/python && \
	pip3 install tensorflow==2.5.2 && \
	python3 -m pytest --cov=graphscope --cov-config=.coveragerc --cov-report=xml --cov-report=term -s -v ./graphscope/tests/minitest

.PHONY: k8stest
k8stest:
	cd $(WORKING_DIR)/python && \
	pip3 install tensorflow==2.5.2 && \
	python3 -m pytest --cov=graphscope --cov-config=.coveragerc --cov-report=xml --cov-report=term -s -v ./graphscope/tests/kubernetes

.PHONY: clean
clean:
	rm -fr $(GAE_BUILD_DIR) || true && \
	rm -fr $(WORKING_DIR)/analytical_engine/proto/ || true && \
	rm -fr $(WORKING_DIR)/learning_engine/graph-learn/cmake-build/ || true && \
	rm -fr ${GL_BUILD_DIR} || true
	rm -fr $(WORKING_DIR)/learning_engine/graph-learn/proto/*.h || true && \
	rm -fr $(WORKING_DIR)/learning_engine/graph-learn/proto/*.cc || true && \
	rm -fr $(WORKING_DIR)/interactive_engine/executor/target || true && \
	rm -fr $(WORKING_DIR)/interactive_engine/assembly/target || true && \
	cd $(WORKING_DIR)/python && python3 setup.py clean --all && \
	cd $(WORKING_DIR)/coordinator && python3 setup.py clean --all
