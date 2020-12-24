%% Load population designs and corresponding metrics, constraints, etc.
%NC: 9 x 2
if exist('NC','var')==0
    error('NC does not exist.')
end 

%CA_all: 36 x 2
if exist('CA_all','var')==0
    error('CA_all does not exist.')
end 

%x_pop: N x 32
if exist('x_pop','var')==0
    error('x_pop does not exist.')
end 
%f_pop: N x 2
if exist('f_pop','var')==0
    error('f_pop does not exist.')
end 
%feas_scores: N x 1
if exist('feas_scores','var')==0
    error('feas_scores does not exist.')
end 
%stab_scores: N x 1
if exist('stab_scores','var')==0
    error('stab_scores does not exist.')
end 
%% Find features of pareto designs and off-pareto designs
pareto_bool = paretofront(f_pop);
f_pareto = f_pop(pareto_bool==1,:);

data = horzcat(x_pop, stab_scores<1);

minSup = 0.1;
minConf = 0.1;
nRules = 200;
sortFlag = 2;
n_categ = size(data,2);
labels = string(cellfun(@(c) num2str(c), num2cell(linspace(1,n_categ,n_categ),1), 'UniformOutput', false));

fname = 'Truss_ARM_results';

[Rules, RuleSup, RuleConf, nTarget, nFeature] = findRules(data, minSup, minConf, nRules, sortFlag, labels, fname);
disp(['See the file named ' fname '.txt for the association rules']);
Rules = cat(2, Rules{:});
nRules = size(Rules, 1);

%Finding chromosomes from rules
Rules_chrom=[];
for i=1:nRules
    t = Rules(i,:);
    t = cat(2,t{:});
    t = ismember(linspace(1,n_categ-1,n_categ-1), t);
    Rules_chrom = [Rules_chrom; t];
end

Rules_chrom = unique(Rules_chrom, 'rows','stable');
n_unique = size(Rules_chrom, 1);
for i=1:12
    subplot(3,4,i)
    visualize_truss_fromx_3x3(NC, CA_all, Rules_chrom(i,:))
end
